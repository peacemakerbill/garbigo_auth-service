package com.garbigo.auth.util;

import java.util.regex.Pattern;

public final class PhoneNumbers {

    private static final Pattern KENYAN = Pattern.compile("^(?:\\+254|254|0)([17]\\d{8})$");

    private PhoneNumbers() {
    }

    public static String normalizeKenyan(String input) {
        if (input == null) {
            return null;
        }
        String compact = input.replaceAll("[\\s-]", "");
        var matcher = KENYAN.matcher(compact);
        return matcher.matches() ? "254" + matcher.group(1) : null;
    }
}