package com.garbigo.auth.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NetworkMountCheck {

    public enum Protocol {
        SMB, NFS;

        public static Protocol parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public record Result(boolean mounted, String problem) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String problem) {
            return new Result(false, problem);
        }
    }

    private static final Path MOUNT_INFO = Paths.get("/proc/self/mountinfo");
    private static final Set<String> SMB_TYPES = Set.of("cifs", "smb3", "smbfs");
    private static final Set<String> NFS_TYPES = Set.of("nfs", "nfs4");

    private NetworkMountCheck() {
    }

    public static List<String> readMountInfo() throws IOException {
        return Files.readAllLines(MOUNT_INFO);
    }

    public static Result verify(List<String> mountInfoLines, Path mountPath, Protocol protocol, String host, String share) {
        Path wanted = mountPath.toAbsolutePath().normalize();
        String fsType = null;
        String source = null;

        for (String line : mountInfoLines) {
            String[] parts = line.trim().split(" ");
            int dash = -1;
            for (int i = 6; i < parts.length; i++) {
                if (parts[i].equals("-")) {
                    dash = i;
                    break;
                }
            }
            if (parts.length < 10 || dash < 0 || dash + 2 >= parts.length) {
                continue;
            }
            if (Paths.get(unescape(parts[4])).normalize().equals(wanted)) {
                fsType = parts[dash + 1];
                source = unescape(parts[dash + 2]);
            }
        }

        if (fsType == null) {
            return Result.fail("nothing is mounted at " + wanted + ". Mount the " + protocol + " share there first");
        }

        Set<String> allowed = protocol == Protocol.SMB ? SMB_TYPES : NFS_TYPES;
        if (!allowed.contains(fsType.toLowerCase(Locale.ENGLISH))) {
            return Result.fail(wanted + " is a '" + fsType + "' mount, not an " + protocol + " share");
        }

        String actualHost;
        String actualShare;
        if (protocol == Protocol.SMB) {
            String normalized = source.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            int slash = normalized.indexOf('/');
            actualHost = slash < 0 ? normalized : normalized.substring(0, slash);
            actualShare = slash < 0 ? "" : normalized.substring(slash + 1);
        } else {
            int colon;
            if (source.startsWith("[")) {
                int close = source.indexOf(']');
                colon = close < 0 ? -1 : source.indexOf(':', close);
            } else {
                colon = source.indexOf(':');
            }
            if (colon < 0) {
                return Result.fail("the mounted source '" + source + "' is not in the host:/path form");
            }
            actualHost = source.substring(0, colon);
            actualShare = source.substring(colon + 1);
        }

        if (!isBlank(host) && !stripBrackets(actualHost).equalsIgnoreCase(stripBrackets(host.trim()))) {
            return Result.fail("the share mounted at " + wanted + " comes from '" + actualHost
                    + "', not the configured host '" + host.trim() + "'");
        }

        if (!isBlank(share) && !sameShare(protocol, actualShare, share)) {
            return Result.fail("the share mounted at " + wanted + " is '" + actualShare
                    + "', not the configured share '" + share.trim() + "'");
        }

        return Result.ok();
    }

    private static boolean sameShare(Protocol protocol, String actual, String configured) {
        String a = trimSlashes(actual);
        String c = trimSlashes(configured.trim());
        if (protocol == Protocol.SMB) {
            String lowerA = a.toLowerCase(Locale.ENGLISH);
            String lowerC = c.toLowerCase(Locale.ENGLISH);
            return lowerA.equals(lowerC) || lowerA.startsWith(lowerC + "/");
        }
        return a.equals(c) || a.startsWith(c + "/");
    }

    private static String trimSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String stripBrackets(String value) {
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String unescape(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && isOctal(value, i + 1)) {
                out.append((char) Integer.parseInt(value.substring(i + 1, i + 4), 8));
                i += 3;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static boolean isOctal(String value, int start) {
        if (start + 3 > value.length()) {
            return false;
        }
        for (int i = start; i < start + 3; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '7') {
                return false;
            }
        }
        return true;
    }
}