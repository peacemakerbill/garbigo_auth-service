package com.garbigo.auth.service;

import com.garbigo.auth.config.StorageProperties;
import com.garbigo.auth.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DiskStorageService {

    private static final Logger log = LoggerFactory.getLogger(DiskStorageService.class);
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._-]{0,119}");

    public record StoredFile(String path, String name, long sizeBytes) {
    }

    @FunctionalInterface
    interface MountInfoSource {
        List<String> read() throws IOException;
    }

    public enum Mode {
        LOCAL, NETWORK
    }

    private final Mode mode;
    private final Path root;
    private final String problem;
    private final String applicationsFolder;
    private final boolean restrictPermissions;
    private final MountInfoSource mountInfo;

    private final Path mountPath;
    private final NetworkMountCheck.Protocol protocol;
    private final String host;
    private final String share;
    private final boolean requireMount;

    @Autowired
    public DiskStorageService(StorageProperties properties) {
        this(properties, NetworkMountCheck::readMountInfo);
    }

    DiskStorageService(StorageProperties properties, MountInfoSource mountInfo) {
        this.mountInfo = mountInfo;
        this.applicationsFolder = blank(properties.getApplicationsFolder())
                ? "collector-applications" : properties.getApplicationsFolder().trim();
        this.restrictPermissions = properties.isRestrictPermissions();

        StorageProperties.Network network = properties.getNetwork() == null
                ? new StorageProperties.Network() : properties.getNetwork();
        this.host = blank(network.getHost()) ? null : network.getHost().trim();
        this.share = blank(network.getShare()) ? null : network.getShare().trim();
        this.requireMount = network.isRequireMount();

        Mode parsedMode = parseMode(properties.getMode());
        NetworkMountCheck.Protocol parsedProtocol = NetworkMountCheck.Protocol.parse(network.getProtocol());
        Path resolvedMount = null;
        Path resolvedRoot = null;
        String configProblem = null;

        if (parsedMode == null) {
            configProblem = "STORAGE_MODE must be LOCAL or NETWORK, but it is '" + properties.getMode() + "'.";
        } else if (parsedMode == Mode.LOCAL) {
            if (blank(properties.getRoot())) {
                configProblem = "STORAGE_ROOT is not set. Set it in .env to the folder where documents should be saved.";
            } else {
                resolvedRoot = Paths.get(properties.getRoot().trim()).toAbsolutePath().normalize();
            }
        } else {
            if (parsedProtocol == null) {
                configProblem = "STORAGE_NETWORK_PROTOCOL must be SMB or NFS, but it is '" + network.getProtocol() + "'.";
            } else if (blank(network.getMountPath())) {
                configProblem = "STORAGE_NETWORK_MOUNT_PATH is not set. Set it to the folder where the share is mounted on this server.";
            } else {
                Path mount = Paths.get(network.getMountPath().trim());
                if (!mount.isAbsolute()) {
                    configProblem = "STORAGE_NETWORK_MOUNT_PATH must be a full path such as /mnt/garbigo-nas.";
                } else {
                    resolvedMount = mount.normalize();
                    String directory = blank(network.getDirectory()) ? "" : network.getDirectory().trim();
                    if (directory.startsWith("/") || directory.startsWith("\\") || directory.contains("..")
                            || directory.indexOf('\0') >= 0) {
                        configProblem = "STORAGE_NETWORK_DIRECTORY must be a folder name inside the share, "
                                + "without a leading slash or '..'.";
                    } else {
                        resolvedRoot = resolvedMount.resolve(directory).normalize();
                        if (!resolvedRoot.startsWith(resolvedMount)) {
                            configProblem = "STORAGE_NETWORK_DIRECTORY must stay inside the mounted share.";
                            resolvedRoot = null;
                        }
                    }
                }
            }
        }

        this.mode = parsedMode == null ? Mode.LOCAL : parsedMode;
        this.protocol = parsedProtocol;
        this.mountPath = resolvedMount;
        this.root = configProblem == null ? resolvedRoot : null;
        this.problem = configProblem;
    }

    @jakarta.annotation.PostConstruct
    void prepare() {
        if (root == null) {
            log.error("STORAGE: {} Document uploads are unavailable until this is fixed.", problem);
            return;
        }

        if (mode == Mode.NETWORK) {
            if (requireMount) {
                NetworkMountCheck.Result status = mountStatus();
                if (!status.mounted()) {
                    log.error("STORAGE: the network share is not ready: {}. Document uploads will fail until it is mounted.",
                            status.problem());
                    return;
                }
            } else {
                log.warn("STORAGE: STORAGE_NETWORK_REQUIRE_MOUNT is false, so documents are saved on the local disk "
                        + "if the share is not mounted.");
            }
        }

        try {
            createDirectories(root);
            if (!Files.isWritable(root)) {
                log.error("STORAGE: the storage folder {} is not writable by the service.", root);
                return;
            }
            if (mode == Mode.NETWORK) {
                log.info("STORAGE: documents are stored on the {} share mounted at {}, in {}",
                        protocol, mountPath, root);
            } else {
                log.info("STORAGE: documents are stored in {}", root);
            }
        } catch (Exception e) {
            log.error("STORAGE: could not prepare the storage folder {} ({}). Uploads will fail until it is fixed.",
                    root, e.getClass().getSimpleName());
        }
    }

    public boolean isConfigured() {
        return root != null;
    }

    public Mode mode() {
        return mode;
    }

    public Path rootDirectory() {
        return root;
    }

    public String applicationFolder(String name) {
        return applicationsFolder + "/" + requireSafeSegment(name);
    }

    public StoredFile upload(String folder, String storedName, byte[] data) {
        requireAvailable();
        String relative = folder + "/" + requireSafeSegment(storedName);
        Path target = resolve(relative);
        Path temp = null;
        try {
            createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, data);
            restrict(temp, false);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
            return new StoredFile(relative, storedName, data.length);
        } catch (IOException e) {
            log.error("STORAGE: could not save {} ({})", relative, e.getClass().getSimpleName());
            throw unavailable();
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public byte[] download(String relativePath) {
        requireAvailable();
        if (relativePath == null || relativePath.isBlank()) {
            throw new CustomException("This file could not be found. It may have been removed from storage.");
        }
        Path file = resolve(relativePath);
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new CustomException("This file could not be found. It may have been removed from storage.");
        } catch (IOException e) {
            log.error("STORAGE: could not read {} ({})", relativePath, e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    public void deleteQuietly(String relativePath) {
        if (root == null || relativePath == null || relativePath.isBlank()) {
            return;
        }
        if (mode == Mode.NETWORK && requireMount) {
            NetworkMountCheck.Result status = mountStatus();
            if (!status.mounted()) {
                log.warn("STORAGE: skipped deleting {} because the network share is not ready: {}",
                        relativePath, status.problem());
                return;
            }
        }
        try {
            Path file = resolve(relativePath);
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null && !parent.equals(root) && isEmptyDirectory(parent)) {
                Files.deleteIfExists(parent);
            }
        } catch (Exception e) {
            log.warn("STORAGE: could not delete {} ({})", relativePath, e.getClass().getSimpleName());
        }
    }

    Path resolve(String relativePath) {
        if (relativePath.startsWith("/") || relativePath.startsWith("\\") || relativePath.contains("..")
                || relativePath.indexOf('\0') >= 0) {
            throw new CustomException("This file could not be found. It may have been removed from storage.");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new CustomException("This file could not be found. It may have been removed from storage.");
        }
        return resolved;
    }

    private void requireAvailable() {
        if (root == null) {
            log.error("STORAGE: {}", problem);
            throw unavailable();
        }
        if (mode == Mode.NETWORK && requireMount) {
            NetworkMountCheck.Result status = mountStatus();
            if (!status.mounted()) {
                log.error("STORAGE: the network share is not ready: {}", status.problem());
                throw unavailable();
            }
        }
    }

    private NetworkMountCheck.Result mountStatus() {
        try {
            return NetworkMountCheck.verify(mountInfo.read(), mountPath, protocol, host, share);
        } catch (IOException | RuntimeException e) {
            return NetworkMountCheck.Result.fail("the list of mounted drives could not be read ("
                    + e.getClass().getSimpleName() + "). The mount check only works on Linux. "
                    + "Use STORAGE_MODE=LOCAL, or set STORAGE_NETWORK_REQUIRE_MOUNT=false");
        }
    }

    private static Mode parseMode(String value) {
        if (blank(value)) {
            return Mode.LOCAL;
        }
        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireSafeSegment(String name) {
        if (name == null || !SAFE_SEGMENT.matcher(name).matches() || name.contains("..")) {
            throw new IllegalArgumentException("Unsafe storage name");
        }
        return name;
    }

    private void createDirectories(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path current = directory;
        while (current != null && current.startsWith(root) && !current.equals(root)) {
            restrict(current, true);
            current = current.getParent();
        }
    }

    private void restrict(Path path, boolean directory) {
        if (!restrictPermissions) {
            return;
        }
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } catch (Exception ignored) {
        }
    }

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static CustomException unavailable() {
        return new CustomException("We couldn't reach document storage right now. Please try again in a few minutes.");
    }
}