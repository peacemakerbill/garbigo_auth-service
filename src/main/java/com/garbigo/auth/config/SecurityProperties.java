package com.garbigo.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private String initialAdminEmails = "";
    private String autoAdminEmails = "";
    private int adminInvitationResendDays = 7;
    private String adminRegisterPath = "/register";

    private boolean loginEmailNotifications = true;
    private boolean newDeviceAlerts = true;
    private boolean notifyUntrustedDevices = true;
    private boolean suspiciousLoginAlerts = true;
    private int notificationCooldownMinutes = 60;
    private int suspiciousAlertCooldownMinutes = 15;

    private boolean deviceTrustEnabled = true;
    private boolean sessionRevocationEnabled = true;
    private boolean allowUntrackedTokens = true;
    private boolean reportRevokesAllSessions = true;
    private int maxDevicesPerUser = 50;
    private int recentAuthMinutes = 15;
    private int actionTokenTtlMinutes = 1440;

    private String deviceCookieName = "gb_device";
    private boolean deviceCookieSecure = true;
    private String deviceCookieSameSite = "Lax";
    private int deviceCookieDays = 180;

    private String trustedProxies = "";

    private boolean ipGeolocationEnabled = false;
    private String ipGeolocationUrl = "https://ipinfo.io/{ip}/json";
    private String ipGeolocationToken = "";
    private int ipGeolocationTimeoutMs = 2000;

    private int maxFailedLoginsPerEmailAndIp = 5;
    private int maxFailedLoginsPerIp = 30;
    private int maxFailedLoginsPerEmail = 20;
    private int failureWindowMinutes = 15;
    private int lockoutMinutes = 5;
    private int maxLockoutMinutes = 60;

    private int suspiciousWindowMinutes = 60;
    private int suspiciousFailedThreshold = 5;
    private int suspiciousAddressThreshold = 3;
    private int suspiciousFrequencyThreshold = 10;

    private int auditRetentionDays = 180;
    private int retentionIntervalMinutes = 360;

    private String displayTimeZone = "UTC";
    private String securityReviewPath = "/account/security";
    private String reportPath = "/account/security/report";

    private boolean hideAccountExistenceOnSignup = false;

    private boolean adminVerificationRequiresNewPassword = true;
}
