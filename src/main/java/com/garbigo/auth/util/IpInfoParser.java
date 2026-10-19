package com.garbigo.auth.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IpInfoParser {

    private static final int MAX_BODY = 4096;

    private IpInfoParser() {
    }

    public static GeoLocation parse(String json) {
        if (json == null || json.isBlank()) {
            return GeoLocation.UNKNOWN;
        }
        String body = json.length() > MAX_BODY ? json.substring(0, MAX_BODY) : json;

        String country = countryName(field(body, "country"));
        String region = SafeText.clean(field(body, "region"), 80);
        String city = SafeText.clean(field(body, "city"), 80);

        if (country == null && region == null && city == null) {
            return GeoLocation.UNKNOWN;
        }
        return new GeoLocation(country, region, city);
    }

    private static String field(String body, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){0,100})\"").matcher(body);
        if (!m.find()) {
            return null;
        }
        return unescape(m.group(1));
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        try {
                            out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                            out.append('u');
                        }
                    } else {
                        out.append('u');
                    }
                }
                case 'n', 't', 'r' -> out.append(' ');
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    private static String countryName(String value) {
        String cleaned = SafeText.clean(value, 80);
        if (cleaned == null) {
            return null;
        }
        if (cleaned.length() == 2 && cleaned.chars().allMatch(Character::isLetter)) {
            String name = Locale.of("", cleaned.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH);
            return name == null || name.isBlank() ? cleaned.toUpperCase(Locale.ROOT) : name;
        }
        return cleaned;
    }
}
