package com.garbigo.auth.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class EmailAddresses {

    private static final int MAX_LENGTH = 254;

    private static final Pattern VALID = Pattern.compile(
            "^[A-Za-z0-9._%+'-]{1,64}@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$");

    private static final Pattern SEPARATORS = Pattern.compile("[,;\\s]+");

    private EmailAddresses() {
    }

    public record ParsedList(Set<String> valid, List<String> invalidMasked) {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = stripWrappingQuotes(raw.trim()).trim().toLowerCase(Locale.ROOT);
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static boolean isValid(String email) {
        if (email == null || email.length() > MAX_LENGTH) {
            return false;
        }
        if (email.contains("..")) {
            return false;
        }
        return VALID.matcher(email).matches();
    }

    public static ParsedList parseList(String raw) {
        Set<String> valid = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();

        if (raw == null || raw.isBlank()) {
            return new ParsedList(valid, invalid);
        }

        for (String part : SEPARATORS.split(stripWrappingQuotes(raw.trim()))) {
            String normalized = normalize(part);
            if (normalized == null) {
                continue;
            }
            if (isValid(normalized)) {
                valid.add(normalized);
            } else {
                invalid.add(mask(normalized));
            }
        }
        return new ParsedList(valid, invalid);
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email.length() <= 2 ? "***" : email.substring(0, 2) + "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String stripWrappingQuotes(String value) {
        String result = value;
        while (result.length() >= 2) {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            boolean wrapped = (first == '"' && last == '"') || (first == '\'' && last == '\'');
            if (!wrapped) {
                break;
            }
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }
}
