package com.garbigo.auth.util;

public record ParsedUserAgent(
        String browserName,
        String browserVersion,
        String osName,
        String osVersion,
        String deviceType,
        String label) {

    public static final String DESKTOP = "Desktop";
    public static final String MOBILE = "Mobile";
    public static final String TABLET = "Tablet";
    public static final String API_CLIENT = "App or API client";
    public static final String UNKNOWN = "Unknown";
}
