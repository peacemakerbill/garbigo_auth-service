package com.garbigo.auth.util;

public final class SafeText {

    private SafeText() {
    }

    public static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), maxLength + 8));
        boolean lastWasSpace = false;
        for (int i = 0; i < value.length() && out.length() < maxLength; i++) {
            char c = value.charAt(i);
            boolean blank = Character.isISOControl(c) || Character.isWhitespace(c)
                    || c == ' ' || c == ' ' || c == '​';
            if (blank) {
                if (!lastWasSpace && out.length() > 0) {
                    out.append(' ');
                }
                lastWasSpace = true;
            } else {
                out.append(c);
                lastWasSpace = false;
            }
        }
        String result = out.toString().trim();
        return result.isEmpty() ? null : result;
    }

    public static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
