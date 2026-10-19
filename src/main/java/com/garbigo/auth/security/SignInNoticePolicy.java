package com.garbigo.auth.security;

import com.garbigo.auth.enums.DeviceStatus;
import com.garbigo.auth.enums.RiskLevel;

public final class SignInNoticePolicy {

    public enum Notice {
        NONE,
        NEW_DEVICE,
        UNTRUSTED_DEVICE,
        SUSPICIOUS
    }

    public record Settings(
            boolean loginEmailNotifications,
            boolean newDeviceAlerts,
            boolean notifyUntrustedDevices,
            boolean suspiciousLoginAlerts) {
    }

    private SignInNoticePolicy() {
    }

    public static Notice decide(Settings settings, DeviceStatus deviceStatus, RiskLevel risk,
                                boolean recentlyNotifiedSameFingerprint, boolean recentSuspiciousAlert) {

        if (settings.suspiciousLoginAlerts() && risk != RiskLevel.LOW) {
            return recentSuspiciousAlert ? Notice.NONE : Notice.SUSPICIOUS;
        }

        if (!settings.loginEmailNotifications() || recentlyNotifiedSameFingerprint) {
            return Notice.NONE;
        }

        if (deviceStatus == DeviceStatus.NEW && settings.newDeviceAlerts()) {
            return Notice.NEW_DEVICE;
        }

        if (deviceStatus == DeviceStatus.RECOGNIZED && settings.notifyUntrustedDevices()) {
            return Notice.UNTRUSTED_DEVICE;
        }

        return Notice.NONE;
    }
}
