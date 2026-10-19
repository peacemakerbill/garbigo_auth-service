package com.garbigo.auth.service;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.enums.DeviceStatus;
import com.garbigo.auth.enums.Role;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SignInMethod;
import com.garbigo.auth.model.SignInRecord;
import com.garbigo.auth.model.User;
import com.garbigo.auth.model.UserSession;
import com.garbigo.auth.repository.SignInRecordRepository;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.SignInNoticePolicy;
import com.garbigo.auth.security.SignInNoticePolicy.Notice;
import com.garbigo.auth.security.SuspiciousSignInDetector;
import com.garbigo.auth.service.SecurityEmailService.EmailEvent;
import com.garbigo.auth.util.GeoLocation;
import com.garbigo.auth.util.SafeText;
import com.garbigo.auth.util.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class SecurityNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SecurityNotificationService.class);

    private final SignInRecordRepository signIns;
    private final UserRepository users;
    private final MongoTemplate mongoTemplate;
    private final DeviceService deviceService;
    private final GeoLocationService geoService;
    private final SecurityActionService actionService;
    private final SecurityEmailService emails;
    private final SecurityAuditService audit;
    private final SecurityProperties properties;

    public SecurityNotificationService(SignInRecordRepository signIns, UserRepository users,
                                       MongoTemplate mongoTemplate, DeviceService deviceService,
                                       GeoLocationService geoService, SecurityActionService actionService,
                                       SecurityEmailService emails, SecurityAuditService audit,
                                       SecurityProperties properties) {
        this.signIns = signIns;
        this.users = users;
        this.mongoTemplate = mongoTemplate;
        this.deviceService = deviceService;
        this.geoService = geoService;
        this.actionService = actionService;
        this.emails = emails;
        this.audit = audit;
        this.properties = properties;
    }

    @Async("securityTaskExecutor")
    public void afterSignIn(String signInRecordId) {
        try {
            handleSignIn(signInRecordId);
        } catch (Exception e) {
            log.warn("Sign-in security follow-up failed: {}", e.getClass().getSimpleName());
        }
    }

    void handleSignIn(String signInRecordId) {
        SignInRecord record = signIns.findById(signInRecordId).orElse(null);
        if (record == null || record.getUserId() == null || record.isAlertSent()) {
            return;
        }
        User user = users.findById(record.getUserId()).orElse(null);
        if (user == null || !user.isEnabled()) {
            return;
        }

        Instant now = Instant.now();

        GeoLocation geo = geoService.lookup(record.getIp());
        if (geo.isKnown()) {
            record.setLocationCountry(geo.country());
            record.setLocationRegion(geo.region());
            record.setLocationCity(geo.city());
            deviceService.updateLocation(record.getDeviceId(), geo.describe());
            recordSessionLocation(record.getSessionId(), geo.describe());
        }

        DeviceStatus status = parseStatus(record.getDeviceStatus());
        SuspiciousSignInDetector.Assessment assessment = assess(user, record, geo, status, now);
        record.setRiskLevel(assessment.level().name());
        record.setRiskReasons(assessment.reasons());

        String fingerprint = SecureTokens.sha256Hex(user.getId() + "|" + record.getIp() + "|" + record.getDeviceLabel())
                .substring(0, 32);
        record.setAlertFingerprint(fingerprint);

        boolean recentlyNotified = signIns.existsByUserIdAndAlertFingerprintAndAlertSentTrueAndOccurredAtAfter(
                user.getId(), fingerprint, now.minus(Duration.ofMinutes(properties.getNotificationCooldownMinutes())));
        boolean recentSuspicious = audit.happenedRecentlyForUser(SecurityEventType.SUSPICIOUS_SIGN_IN, user.getId(),
                Duration.ofMinutes(properties.getSuspiciousAlertCooldownMinutes()));

        Notice notice = SignInNoticePolicy.decide(
                new SignInNoticePolicy.Settings(properties.isLoginEmailNotifications(), properties.isNewDeviceAlerts(),
                        properties.isNotifyUntrustedDevices(), properties.isSuspiciousLoginAlerts()),
                status, assessment.level(), recentlyNotified, recentSuspicious);

        signIns.save(record);

        if (notice == Notice.NONE) {
            return;
        }

        EmailEvent event = switch (notice) {
            case SUSPICIOUS -> EmailEvent.SUSPICIOUS_SIGN_IN;
            case NEW_DEVICE -> EmailEvent.NEW_SIGN_IN;
            default -> EmailEvent.UNTRUSTED_SIGN_IN;
        };

        if (notice == Notice.SUSPICIOUS) {
            audit.recordWithIp(SecurityEventType.SUSPICIOUS_SIGN_IN, user.getId(), user.getId(), record.getIp(),
                    "level=" + assessment.level() + ", signals=" + assessment.reasons().size());
        }

        sendSignInEmail(user, record, event, status, assessment, geo);
    }

    @Async("securityTaskExecutor")
    public void sendAdminInvitation(String email) {
        try {
            Map<String, Object> vars = emails.variables(EmailEvent.ADMIN_INVITATION, null, email);
            vars.put("inviteUrl", emails.inviteUrl());
            emails.send(email, EmailEvent.ADMIN_INVITATION, vars);
            audit.recordForEmail(SecurityEventType.ADMIN_INVITATION_SENT, email, "Administrator invitation sent");
        } catch (Exception e) {
            log.warn("Could not send an administrator invitation: {}", e.getClass().getSimpleName());
            audit.recordForEmail(SecurityEventType.ADMIN_INVITATION_FAILED, email,
                    "Administrator invitation could not be sent");
        }
    }

    @Async("securityTaskExecutor")
    public void notifyAdminRoleGranted(String userId, String source, String previousRole) {
        sendToUser(userId, EmailEvent.ADMIN_ROLE_GRANTED, vars -> {
            vars.put("source", source);
            vars.put("previousRole", previousRole);
            vars.put("newRole", Role.ADMIN.name());
            vars.put("occurredAt", emails.formatTime(Instant.now()));
        });
    }

    @Async("securityTaskExecutor")
    public void notifyRoleChanged(String userId, String previousRole, String newRole) {
        sendToUser(userId, EmailEvent.ROLE_CHANGED, vars -> {
            vars.put("previousRole", previousRole);
            vars.put("newRole", newRole);
            vars.put("occurredAt", emails.formatTime(Instant.now()));
        });
    }

    @Async("securityTaskExecutor")
    public void notifyPasswordChanged(String userId, String ip) {
        sendToUser(userId, EmailEvent.PASSWORD_CHANGED, vars -> {
            vars.put("ip", ip);
            vars.put("occurredAt", emails.formatTime(Instant.now()));
        });
    }

    @Async("securityTaskExecutor")
    public void notifyPasswordResetCompleted(String userId, String ip) {
        sendToUser(userId, EmailEvent.PASSWORD_RESET_COMPLETED, vars -> {
            vars.put("ip", ip);
            vars.put("occurredAt", emails.formatTime(Instant.now()));
        });
    }

    @Async("securityTaskExecutor")
    public void notifyEmailChanged(String userId, String previousEmail, String newEmail) {
        User user = users.findById(userId).orElse(null);
        if (user == null || previousEmail == null) {
            return;
        }
        try {
            Map<String, Object> vars = emails.variables(EmailEvent.EMAIL_CHANGED, user.getFirstName(), previousEmail);
            vars.put("newEmailMasked", com.garbigo.auth.util.EmailAddresses.mask(newEmail));
            vars.put("occurredAt", emails.formatTime(Instant.now()));
            emails.send(previousEmail, EmailEvent.EMAIL_CHANGED, vars);
        } catch (Exception e) {
            log.warn("Could not send an email-change notice: {}", e.getClass().getSimpleName());
        }
    }

    @Async("securityTaskExecutor")
    public void notifyAccountLocked(String userId, long minutes, String ip) {
        sendToUser(userId, EmailEvent.ACCOUNT_LOCKED, vars -> {
            vars.put("lockMinutes", Math.max(1, minutes));
            vars.put("ip", ip);
            vars.put("occurredAt", emails.formatTime(Instant.now()));
        });
    }

    @Async("securityTaskExecutor")
    public void notifyReportCompleted(String userId, boolean resetEmailSent) {
        sendToUser(userId, EmailEvent.UNAUTHORIZED_REPORT_COMPLETED, vars -> {
            vars.put("resetEmailSent", resetEmailSent);
            vars.put("occurredAt", emails.formatTime(Instant.now()));
        });
    }

    @Async("securityTaskExecutor")
    public void notifyTokenReplay(String userId, String ip) {
        sendToUser(userId, EmailEvent.TOKEN_REPLAY, vars -> {
            vars.put("ip", ip);
            vars.put("occurredAt", emails.formatTime(Instant.now()));
        });
    }

    @Async("securityTaskExecutor")
    public void notifyAccountAlreadyExists(String email) {
        try {
            Map<String, Object> vars = emails.variables(EmailEvent.ACCOUNT_ALREADY_EXISTS, null, email);
            vars.put("occurredAt", emails.formatTime(Instant.now()));
            emails.send(email, EmailEvent.ACCOUNT_ALREADY_EXISTS, vars);
        } catch (Exception e) {
            log.warn("Could not send an account-exists notice: {}", e.getClass().getSimpleName());
        }
    }

    private void sendToUser(String userId, EmailEvent event, Consumer<Map<String, Object>> customizer) {
        try {
            User user = users.findById(userId).orElse(null);
            if (user == null || user.getEmail() == null) {
                return;
            }
            Map<String, Object> vars = emails.variables(event, user.getFirstName(), user.getEmail());
            customizer.accept(vars);
            emails.send(user.getEmail(), event, vars);
        } catch (Exception e) {
            log.warn("Could not send a {} notice: {}", event, e.getClass().getSimpleName());
        }
    }

    private void sendSignInEmail(User user, SignInRecord record, EmailEvent event, DeviceStatus status,
                                 SuspiciousSignInDetector.Assessment assessment, GeoLocation geo) {
        try {
            Map<String, Object> vars = emails.variables(event, user.getFirstName(), user.getEmail());
            vars.put("occurredAt", emails.formatTime(record.getOccurredAt()));
            vars.put("ip", record.getIp());
            vars.put("location", geo.describe());
            vars.put("deviceLabel", record.getDeviceLabel());
            vars.put("deviceType", record.getDeviceType());
            vars.put("os", join(record.getOsName(), record.getOsVersion()));
            vars.put("browser", join(record.getBrowserName(), record.getBrowserVersion()));
            vars.put("signInMethod", methodLabel(record.getSignInMethod()));
            vars.put("deviceStatus", statusLabel(status));
            vars.put("reasons", assessment.reasons());

            if (properties.isSessionRevocationEnabled()) {
                vars.put("reportUrl", emails.reportUrl(actionService.issueReportToken(user, record)));
            }

            emails.send(user.getEmail(), event, vars);

            record.setAlertSent(true);
            signIns.save(record);
            if (event != EmailEvent.SUSPICIOUS_SIGN_IN) {
                audit.recordWithIp(SecurityEventType.SIGN_IN_ALERT_SENT, user.getId(), user.getId(), record.getIp(),
                        "event=" + event.name() + ", device=" + status.name());
            }
        } catch (Exception e) {
            log.warn("Could not send a sign-in notice: {}", e.getClass().getSimpleName());
            audit.recordWithIp(SecurityEventType.SIGN_IN_ALERT_FAILED, user.getId(), user.getId(), record.getIp(),
                    "event=" + event.name());
        }
    }

    private SuspiciousSignInDetector.Assessment assess(User user, SignInRecord record, GeoLocation geo,
                                                        DeviceStatus status, Instant now) {
        Instant windowStart = now.minus(Duration.ofMinutes(properties.getSuspiciousWindowMinutes()));
        List<SignInRecord> recent = signIns.findByUserIdAndOccurredAtAfter(user.getId(), windowStart);

        Instant lastSuccessBefore = recent.stream()
                .filter(r -> r.isSuccess() && !Objects.equals(r.getId(), record.getId()))
                .map(SignInRecord::getOccurredAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        int failures = (int) recent.stream()
                .filter(r -> !r.isSuccess())
                .filter(r -> lastSuccessBefore == null || (r.getOccurredAt() != null
                        && r.getOccurredAt().isAfter(lastSuccessBefore)))
                .count();
        int successes = (int) recent.stream().filter(SignInRecord::isSuccess).count();

        Set<String> addresses = new HashSet<>();
        for (SignInRecord r : recent) {
            if (r.getIp() != null) {
                addresses.add(r.getIp());
            }
        }

        SignInRecord previous = signIns.findTop10ByUserIdAndSuccessTrueOrderByOccurredAtDesc(user.getId()).stream()
                .filter(r -> !Objects.equals(r.getId(), record.getId()))
                .filter(r -> r.getLocationCountry() != null)
                .findFirst()
                .orElse(null);

        SuspiciousSignInDetector.Signals signals = new SuspiciousSignInDetector.Signals(
                status,
                user.getRole() == Role.ADMIN,
                failures,
                addresses.size(),
                successes,
                previous == null ? null : previous.getLocationCountry(),
                geo.country(),
                previous == null ? null : previous.getLocationRegion(),
                geo.region());

        return SuspiciousSignInDetector.evaluate(signals, new SuspiciousSignInDetector.Thresholds(
                properties.getSuspiciousFailedThreshold(),
                properties.getSuspiciousAddressThreshold(),
                properties.getSuspiciousFrequencyThreshold()));
    }

    private void recordSessionLocation(String sessionId, String location) {
        if (sessionId == null || location == null) {
            return;
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(sessionId)),
                new Update().set("location", location), UserSession.class);
    }

    private static DeviceStatus parseStatus(String value) {
        try {
            return DeviceStatus.valueOf(value);
        } catch (Exception e) {
            return DeviceStatus.NEW;
        }
    }

    private static String join(String name, String version) {
        if (name == null) {
            return null;
        }
        String major = version == null ? null : version.split("\\.")[0];
        return SafeText.clean(major == null ? name : name + " " + (version.length() > 12 ? major : version), 60);
    }

    private static String methodLabel(String method) {
        try {
            return SignInMethod.valueOf(method).getLabel();
        } catch (Exception e) {
            return method;
        }
    }

    private static String statusLabel(DeviceStatus status) {
        return switch (status) {
            case NEW -> "New device";
            case RECOGNIZED -> "Recognized, not marked as trusted";
            case TRUSTED -> "Trusted device";
        };
    }
}
