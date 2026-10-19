package com.garbigo.auth.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserAgentParser {

    private static final int MAX_INPUT = 400;

    private static final Pattern EDGE = Pattern.compile("\\bEdg(?:e|A|iOS)?/([0-9.]{1,20})");
    private static final Pattern OPERA = Pattern.compile("\\bOPR/([0-9.]{1,20})");
    private static final Pattern SAMSUNG = Pattern.compile("\\bSamsungBrowser/([0-9.]{1,20})");
    private static final Pattern FIREFOX = Pattern.compile("\\b(?:Firefox|FxiOS)/([0-9.]{1,20})");
    private static final Pattern CHROME = Pattern.compile("\\b(?:Chrome|CriOS)/([0-9.]{1,20})");
    private static final Pattern SAFARI = Pattern.compile("\\bVersion/([0-9.]{1,20}).{0,80}\\bSafari/");
    private static final Pattern POSTMAN = Pattern.compile("\\bPostmanRuntime/([0-9.]{1,20})");
    private static final Pattern CURL = Pattern.compile("\\bcurl/([0-9.]{1,20})");
    private static final Pattern OKHTTP = Pattern.compile("\\bokhttp/([0-9.]{1,20})");
    private static final Pattern DART = Pattern.compile("\\bDart/([0-9.]{1,20})");
    private static final Pattern PYTHON = Pattern.compile("\\bpython-requests/([0-9.]{1,20})");

    private static final Pattern WINDOWS = Pattern.compile("Windows NT ([0-9.]{1,6})");
    private static final Pattern ANDROID = Pattern.compile("Android ([0-9.]{1,10})");
    private static final Pattern IOS = Pattern.compile("(?:iPhone|iPad|iPod).{0,40}?OS ([0-9_]{1,10})");
    private static final Pattern MACOS = Pattern.compile("Mac OS X ([0-9_.]{1,12})");

    private UserAgentParser() {
    }

    public static ParsedUserAgent parse(String rawUserAgent) {
        if (rawUserAgent == null || rawUserAgent.isBlank()) {
            return new ParsedUserAgent(null, null, null, null, ParsedUserAgent.UNKNOWN, "Unknown device");
        }

        String ua = rawUserAgent.length() > MAX_INPUT ? rawUserAgent.substring(0, MAX_INPUT) : rawUserAgent;

        String[] browser = detectBrowser(ua);
        String[] os = detectOs(ua);
        String deviceType = detectDeviceType(ua, browser[0], os[0]);

        String browserName = SafeText.clean(browser[0], 40);
        String browserVersion = SafeText.clean(browser[1], 20);
        String osName = SafeText.clean(os[0], 40);
        String osVersion = SafeText.clean(os[1], 20);

        return new ParsedUserAgent(browserName, browserVersion, osName, osVersion, deviceType,
                buildLabel(browserName, browserVersion, osName, osVersion));
    }

    private static String[] detectBrowser(String ua) {
        Matcher m;
        if ((m = EDGE.matcher(ua)).find()) return new String[] {"Edge", m.group(1)};
        if ((m = OPERA.matcher(ua)).find()) return new String[] {"Opera", m.group(1)};
        if ((m = SAMSUNG.matcher(ua)).find()) return new String[] {"Samsung Internet", m.group(1)};
        if ((m = FIREFOX.matcher(ua)).find()) return new String[] {"Firefox", m.group(1)};
        if ((m = CHROME.matcher(ua)).find()) return new String[] {"Chrome", m.group(1)};
        if ((m = SAFARI.matcher(ua)).find()) return new String[] {"Safari", m.group(1)};
        if ((m = POSTMAN.matcher(ua)).find()) return new String[] {"Postman", m.group(1)};
        if ((m = CURL.matcher(ua)).find()) return new String[] {"curl", m.group(1)};
        if ((m = OKHTTP.matcher(ua)).find()) return new String[] {"OkHttp", m.group(1)};
        if ((m = DART.matcher(ua)).find()) return new String[] {"Dart app", m.group(1)};
        if ((m = PYTHON.matcher(ua)).find()) return new String[] {"Python requests", m.group(1)};
        return new String[] {null, null};
    }

    private static String[] detectOs(String ua) {
        Matcher m;
        if ((m = IOS.matcher(ua)).find()) return new String[] {"iOS", m.group(1).replace('_', '.')};
        if ((m = ANDROID.matcher(ua)).find()) return new String[] {"Android", m.group(1)};
        if ((m = WINDOWS.matcher(ua)).find()) return new String[] {"Windows", windowsVersion(m.group(1))};
        if ((m = MACOS.matcher(ua)).find()) return new String[] {"macOS", m.group(1).replace('_', '.')};
        if (ua.contains("CrOS")) return new String[] {"ChromeOS", null};
        if (ua.contains("Linux") || ua.contains("X11")) return new String[] {"Linux", null};
        return new String[] {null, null};
    }

    private static String windowsVersion(String nt) {
        return switch (nt) {
            case "10.0" -> "10 or 11";
            case "6.3" -> "8.1";
            case "6.2" -> "8";
            case "6.1" -> "7";
            default -> nt;
        };
    }

    private static String detectDeviceType(String ua, String browserName, String osName) {
        boolean tool = browserName != null && (browserName.equals("Postman") || browserName.equals("curl")
                || browserName.equals("OkHttp") || browserName.equals("Dart app")
                || browserName.equals("Python requests"));
        if (tool) {
            return ParsedUserAgent.API_CLIENT;
        }
        if (ua.contains("iPad") || ua.contains("Tablet")) {
            return ParsedUserAgent.TABLET;
        }
        if ("Android".equals(osName)) {
            return ua.contains("Mobile") ? ParsedUserAgent.MOBILE : ParsedUserAgent.TABLET;
        }
        if ("iOS".equals(osName) || ua.contains("Mobile")) {
            return ParsedUserAgent.MOBILE;
        }
        if (osName != null) {
            return ParsedUserAgent.DESKTOP;
        }
        return ParsedUserAgent.UNKNOWN;
    }

    private static String buildLabel(String browserName, String browserVersion, String osName, String osVersion) {
        StringBuilder label = new StringBuilder();
        if (browserName != null) {
            label.append(browserName);
            if (browserVersion != null) {
                label.append(' ').append(browserVersion.split("\\.")[0]);
            }
        }
        if (osName != null) {
            if (label.length() > 0) {
                label.append(" on ");
            }
            label.append(osName);
            if (osVersion != null) {
                label.append(' ').append(osVersion);
            }
        }
        return label.length() == 0 ? "Unknown device" : label.toString();
    }
}
