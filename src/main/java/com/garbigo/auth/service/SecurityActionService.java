package com.garbigo.auth.service;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.dto.SecurityDtos.ActionPreviewDto;
import com.garbigo.auth.dto.SecurityDtos.ActionResultDto;
import com.garbigo.auth.enums.SecurityActionType;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SessionRevokeReason;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.SecurityActionToken;
import com.garbigo.auth.model.SignInRecord;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.SecurityActionTokenRepository;
import com.garbigo.auth.repository.SignInRecordRepository;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.ClientContextFactory;
import com.garbigo.auth.util.SecureTokens;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SecurityActionService {

    static final String INVALID_LINK = "This link is no longer valid. If you are worried about your account, "
            + "sign in and review your recent activity.";

    private final SecurityActionTokenRepository tokens;
    private final SignInRecordRepository signIns;
    private final UserRepository users;
    private final MongoTemplate mongoTemplate;
    private final SessionService sessionService;
    private final DeviceService deviceService;
    private final AccountRecoveryService recoveryService;
    private final SecurityAuditService audit;
    private final SecurityProperties properties;
    private final ClientContextFactory contexts;
    private final ObjectProvider<SecurityNotificationService> notifications;

    public SecurityActionService(SecurityActionTokenRepository tokens, SignInRecordRepository signIns,
                                 UserRepository users, MongoTemplate mongoTemplate, SessionService sessionService,
                                 DeviceService deviceService, AccountRecoveryService recoveryService,
                                 SecurityAuditService audit, SecurityProperties properties,
                                 ClientContextFactory contexts,
                                 ObjectProvider<SecurityNotificationService> notifications) {
        this.tokens = tokens;
        this.signIns = signIns;
        this.users = users;
        this.mongoTemplate = mongoTemplate;
        this.sessionService = sessionService;
        this.deviceService = deviceService;
        this.recoveryService = recoveryService;
        this.audit = audit;
        this.properties = properties;
        this.contexts = contexts;
        this.notifications = notifications;
    }

    public String issueReportToken(User user, SignInRecord record) {
        String raw = SecureTokens.generate();
        Instant now = Instant.now();

        SecurityActionToken token = new SecurityActionToken();
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        token.setUserId(user.getId());
        token.setType(SecurityActionType.REPORT_UNAUTHORIZED_SIGN_IN);
        token.setSessionId(record.getSessionId());
        token.setDeviceId(record.getDeviceId());
        token.setSignInRecordId(record.getId());
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusSeconds(properties.getActionTokenTtlMinutes() * 60L));
        tokens.save(token);
        return raw;
    }

    public ActionPreviewDto inspect(String rawToken) {
        SecurityActionToken token = findUsable(rawToken)
                .orElseThrow(() -> new CustomException(INVALID_LINK));

        SignInRecord record = token.getSignInRecordId() == null ? null
                : signIns.findById(token.getSignInRecordId()).orElse(null);

        if (record == null) {
            return new ActionPreviewDto(token.getCreatedAt(), null, null, null, null);
        }

        String location = joinLocation(record);
        return new ActionPreviewDto(record.getOccurredAt(), record.getDeviceLabel(), location,
                maskIp(record.getIp()), record.getSignInMethod());
    }

    public ActionResultDto reportUnauthorized(String rawToken, boolean requestPasswordReset) {
        SecurityActionToken token = consume(rawToken)
                .orElseThrow(() -> new CustomException(INVALID_LINK));

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new CustomException(INVALID_LINK));

        int ended;
        if (properties.isReportRevokesAllSessions()) {
            ended = sessionService.revokeAll(user.getId(), SessionRevokeReason.UNAUTHORIZED_SIGN_IN_REPORTED);
        } else {
            ended = 0;
            if (token.getDeviceId() != null) {
                ended += sessionService.revokeByDevice(user.getId(), token.getDeviceId(),
                        SessionRevokeReason.UNAUTHORIZED_SIGN_IN_REPORTED);
            }
        }

        boolean deviceRemoved = deviceService.removeIfPresent(user.getId(), token.getDeviceId());

        boolean resetSent = false;
        if (requestPasswordReset && user.isEnabled()) {
            try {
                recoveryService.sendPasswordResetEmail(user);
                resetSent = true;
            } catch (Exception e) {
                resetSent = false;
            }
        }

        audit.recordWithIp(SecurityEventType.UNAUTHORIZED_SIGN_IN_REPORTED, user.getId(), user.getId(),
                contexts.current().ip(),
                "sessionsEnded=" + ended + ", deviceRemoved=" + deviceRemoved + ", resetEmail=" + resetSent);

        SecurityNotificationService notifier = notifications.getIfAvailable();
        if (notifier != null) {
            notifier.notifyReportCompleted(user.getId(), resetSent);
        }

        String message = "Your account has been secured. We signed out the reported session"
                + (properties.isReportRevokesAllSessions() ? " and all your other sessions" : "")
                + (resetSent ? ", and we sent you a link to choose a new password." : ". We recommend changing your password.");
        return new ActionResultDto(message, ended, deviceRemoved, resetSent);
    }

    public void removeExpired(Instant cutoff) {
        tokens.deleteByExpiresAtBefore(cutoff);
    }

    private Optional<SecurityActionToken> findUsable(String rawToken) {
        if (!SecureTokens.looksLikeToken(rawToken)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return tokens.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(t -> t.getUsedAt() == null)
                .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(now))
                .filter(t -> t.getType() == SecurityActionType.REPORT_UNAUTHORIZED_SIGN_IN);
    }

    private Optional<SecurityActionToken> consume(String rawToken) {
        if (!SecureTokens.looksLikeToken(rawToken)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("tokenHash").is(SecureTokens.sha256Hex(rawToken))
                .and("usedAt").is(null)
                .and("expiresAt").gt(now)
                .and("type").is(SecurityActionType.REPORT_UNAUTHORIZED_SIGN_IN));
        SecurityActionToken consumed = mongoTemplate.findAndModify(query, new Update().set("usedAt", now),
                FindAndModifyOptions.options().returnNew(true), SecurityActionToken.class);
        return Optional.ofNullable(consumed);
    }

    private static String joinLocation(SignInRecord record) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[] {record.getLocationCity(), record.getLocationRegion(),
                record.getLocationCountry()}) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(part);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    static String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".x";
        }
        int lastColon = ip.lastIndexOf(':');
        if (lastColon > 0) {
            return ip.substring(0, lastColon) + ":x";
        }
        return null;
    }
}
