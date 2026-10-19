package com.garbigo.auth.security;

import com.garbigo.auth.enums.DeviceStatus;
import com.garbigo.auth.enums.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SuspiciousSignInDetector {

    public static final int SCORE_NEW_DEVICE = 20;
    public static final int SCORE_FAILED_ATTEMPTS = 40;
    public static final int SCORE_MANY_ADDRESSES = 25;
    public static final int SCORE_HIGH_FREQUENCY = 20;
    public static final int SCORE_COUNTRY_CHANGE = 30;
    public static final int SCORE_REGION_CHANGE = 10;
    public static final int SCORE_ADMIN_EXTRA = 15;

    public static final int MEDIUM_FROM = 40;
    public static final int HIGH_FROM = 60;

    private SuspiciousSignInDetector() {
    }

    public record Thresholds(int failedAttempts, int distinctAddresses, int signInsPerWindow) {
    }

    public record Signals(
            DeviceStatus deviceStatus,
            boolean adminAccount,
            int recentFailures,
            int distinctRecentAddresses,
            int recentSignIns,
            String previousCountry,
            String currentCountry,
            String previousRegion,
            String currentRegion) {
    }

    public record Assessment(RiskLevel level, int score, List<String> reasons) {
    }

    public static Assessment evaluate(Signals s, Thresholds t) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (s.deviceStatus() == DeviceStatus.NEW) {
            score += SCORE_NEW_DEVICE;
            reasons.add("This is a device we have not seen on your account before.");
        }

        if (s.recentFailures() >= t.failedAttempts()) {
            score += SCORE_FAILED_ATTEMPTS;
            reasons.add("There were several failed sign-in attempts shortly before this one.");
        }

        if (s.distinctRecentAddresses() >= t.distinctAddresses()) {
            score += SCORE_MANY_ADDRESSES;
            reasons.add("Sign-ins came from several different network addresses in a short time.");
        }

        if (s.recentSignIns() >= t.signInsPerWindow()) {
            score += SCORE_HIGH_FREQUENCY;
            reasons.add("There have been unusually many sign-ins in a short time.");
        }

        if (differs(s.previousCountry(), s.currentCountry())) {
            score += SCORE_COUNTRY_CHANGE;
            reasons.add("The approximate location is in a different country than your previous sign-in.");
        } else if (sameKnown(s.previousCountry(), s.currentCountry())
                && differs(s.previousRegion(), s.currentRegion())) {
            score += SCORE_REGION_CHANGE;
            reasons.add("The approximate location is in a different region than your previous sign-in.");
        }

        if (s.adminAccount() && score >= 30) {
            score += SCORE_ADMIN_EXTRA;
            reasons.add("This is an administrator account, so we apply stricter checks.");
        }

        RiskLevel level = score >= HIGH_FROM ? RiskLevel.HIGH : score >= MEDIUM_FROM ? RiskLevel.MEDIUM : RiskLevel.LOW;
        return new Assessment(level, score, Collections.unmodifiableList(reasons));
    }

    private static boolean differs(String a, String b) {
        return a != null && b != null && !a.isBlank() && !b.isBlank() && !a.equalsIgnoreCase(b);
    }

    private static boolean sameKnown(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.equalsIgnoreCase(b);
    }
}
