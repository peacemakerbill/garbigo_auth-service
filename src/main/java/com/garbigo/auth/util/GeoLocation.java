package com.garbigo.auth.util;

public record GeoLocation(String country, String region, String city) {

    public static final GeoLocation UNKNOWN = new GeoLocation(null, null, null);

    public boolean isKnown() {
        return country != null || region != null || city != null;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        append(sb, city);
        append(sb, region);
        append(sb, country);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part);
    }
}
