package com.garbigo.auth.service;

import com.garbigo.auth.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.regex.Pattern;

@Service
public class DiskStorageService {

    private static final Logger log = LoggerFactory.getLogger(DiskStorageService.class);
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._-]{0,119}");

    public record StoredFile(String path, String name, long sizeBytes) {
    }

    private final Path root;
    private final String applicationsFolder;
    private final boolean restrictPermissions;

    public DiskStorageService(@Value("${app.storage.root:}") String rootPath,
                              @Value("${app.storage.applications-folder:collector-applications}") String applicationsFolder,
                              @Value("${app.storage.restrict-permissions:true}") boolean restrictPermissions) {
        this.root = rootPath == null || rootPath.isBlank() ? null : Paths.get(rootPath.trim()).toAbsolutePath().normalize();
        this.applicationsFolder = applicationsFolder == null || applicationsFolder.isBlank()
                ? "collector-applications" : applicationsFolder.trim();
        this.restrictPermissions = restrictPermissions;
    }

    @jakarta.annotation.PostConstruct
    void prepare() {
        if (root == null) {
            log.error("STORAGE: STORAGE_ROOT is not set, so document uploads are unavailable. "
                    + "Set it in .env to the folder where documents should be saved.");
            return;
        }
        try {
            createDirectories(root);
            if (!Files.isWritable(root)) {
                log.error("STORAGE: the storage folder {} is not writable by the service.", root);
                return;
            }
            log.info("STORAGE: documents are stored in {}", root);
        } catch (Exception e) {
            log.error("STORAGE: could not prepare the storage folder {} ({}). Uploads will fail until it is fixed.",
                    root, e.getClass().getSimpleName());
        }
    }

    public boolean isConfigured() {
        return root != null;
    }

    public Path rootDirectory() {
        return root;
    }

    public String applicationFolder(String name) {
        return applicationsFolder + "/" + requireSafeSegment(name);
    }

    public StoredFile upload(String folder, String storedName, byte[] data) {
        requireConfigured();
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
        requireConfigured();
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

    private void requireConfigured() {
        if (root == null) {
            log.error("STORAGE: STORAGE_ROOT is not set. Set it in .env to enable document uploads.");
            throw unavailable();
        }
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