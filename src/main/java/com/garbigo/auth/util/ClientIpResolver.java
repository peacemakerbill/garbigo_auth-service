package com.garbigo.auth.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ClientIpResolver {

    public static final String UNKNOWN = "unknown";

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private final List<Cidr> trustedProxies;

    public ClientIpResolver(String trustedProxiesCsv) {
        this.trustedProxies = parseTrusted(trustedProxiesCsv);
    }

    public String resolve(String remoteAddr, String forwardedFor) {
        String remote = normalizeIp(remoteAddr);
        if (remote == null) {
            return UNKNOWN;
        }
        if (trustedProxies.isEmpty() || !isTrusted(remote) || forwardedFor == null || forwardedFor.isBlank()) {
            return remote;
        }

        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalizeIp(hops[i]);
            if (hop == null) {
                return remote;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return remote;
    }

    public boolean isTrusted(String ip) {
        InetAddress address = toAddress(ip);
        if (address == null) {
            return false;
        }
        byte[] bytes = address.getAddress();
        for (Cidr cidr : trustedProxies) {
            if (cidr.contains(bytes)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeIp(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 64) {
            return null;
        }

        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) {
                return null;
            }
            value = value.substring(1, end);
        } else {
            int firstColon = value.indexOf(':');
            if (firstColon >= 0 && firstColon == value.lastIndexOf(':') && value.indexOf('.') >= 0) {
                value = value.substring(0, firstColon);
            }
        }

        InetAddress address = toAddress(value);
        return address == null ? null : address.getHostAddress();
    }

    public static boolean isPublicAddress(String ip) {
        InetAddress address = toAddress(ip);
        if (address == null) {
            return false;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 0 || first >= 240) {
                return false;
            }
        } else if (b.length == 16) {
            if ((b[0] & 0xfe) == 0xfc) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress toAddress(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.indexOf(':') >= 0) {
                if (!IPV6_CHARS.matcher(value).matches()) {
                    return null;
                }
                return InetAddress.getByName(value);
            }
            var m = IPV4.matcher(value);
            if (!m.matches()) {
                return null;
            }
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int part = Integer.parseInt(m.group(i + 1));
                if (part > 255) {
                    return null;
                }
                bytes[i] = (byte) part;
            }
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException | NumberFormatException e) {
            return null;
        }
    }

    private static List<Cidr> parseTrusted(String csv) {
        List<Cidr> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String entry : csv.split("[,;\\s]+")) {
            if (entry.isBlank()) {
                continue;
            }
            Cidr cidr = Cidr.parse(entry.trim());
            if (cidr != null) {
                result.add(cidr);
            }
        }
        return result;
    }

    private record Cidr(byte[] network, int prefixBits) {

        static Cidr parse(String text) {
            String addressPart = text;
            Integer prefix = null;
            int slash = text.indexOf('/');
            if (slash >= 0) {
                addressPart = text.substring(0, slash);
                try {
                    prefix = Integer.parseInt(text.substring(slash + 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            InetAddress address = toAddress(addressPart);
            if (address == null) {
                return null;
            }
            byte[] bytes = address.getAddress();
            int max = bytes.length * 8;
            int bits = prefix == null ? max : prefix;
            if (bits < 0 || bits > max) {
                return null;
            }
            return new Cidr(bytes, bits);
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remaining = prefixBits % 8;
            if (remaining == 0) {
                return true;
            }
            int mask = (0xff << (8 - remaining)) & 0xff;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
