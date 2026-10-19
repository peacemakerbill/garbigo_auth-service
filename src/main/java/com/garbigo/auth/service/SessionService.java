package com.garbigo.auth.service;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SessionRevokeReason;
import com.garbigo.auth.enums.SignInMethod;
import com.garbigo.auth.exception.ApiException;
import com.garbigo.auth.model.User;
import com.garbigo.auth.model.UserDevice;
import com.garbigo.auth.model.UserSession;
import com.garbigo.auth.repository.UserSessionRepository;
import com.garbigo.auth.security.ClientContext;
import com.garbigo.auth.security.JwtUtil;
import com.garbigo.auth.security.SessionPolicy;
import com.garbigo.auth.security.TokenBlacklistService;
import com.garbigo.auth.util.ParsedUserAgent;
import com.garbigo.auth.util.UserAgentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    public record TokenCheck(SessionPolicy.Verdict verdict, UserSession session) {
        public boolean allowed() {
            return verdict == SessionPolicy.Verdict.ALLOWED;
        }
    }

    private final UserSessionRepository sessions;
    private final TokenBlacklistService blacklist;
    private final MongoTemplate mongoTemplate;
    private final SecurityProperties properties;
    private final SecurityAuditService audit;
    private final JwtUtil jwtUtil;

    public SessionService(UserSessionRepository sessions, TokenBlacklistService blacklist, MongoTemplate mongoTemplate,
                          SecurityProperties properties, SecurityAuditService audit, JwtUtil jwtUtil) {
        this.sessions = sessions;
        this.blacklist = blacklist;
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.audit = audit;
        this.jwtUtil = jwtUtil;
    }

    public UserSession create(User user, JwtUtil.GeneratedToken token, UserDevice device, ClientContext ctx,
                              SignInMethod method) {
        ParsedUserAgent agent = UserAgentParser.parse(ctx.userAgent());
        Instant now = Instant.now();

        UserSession session = new UserSession();
        session.setJti(token.jti());
        session.setUserId(user.getId());
        session.setDeviceId(device == null ? null : device.getId());
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(token.expiresAt());
        session.setIp(ctx.ip());
        session.setDeviceLabel(device != null && device.getLabel() != null ? device.getLabel() : agent.label());
        session.setSignInMethod(method == null ? null : method.name());
        return sessions.save(session);
    }

    public TokenCheck check(String jti, User user, Instant issuedAt) {
        Optional<UserSession> found = jti == null ? Optional.empty() : sessions.findByJti(jti);
        long issuedSeconds = issuedAt == null ? 0L : issuedAt.getEpochSecond();

        SessionPolicy.Verdict verdict = SessionPolicy.evaluate(
                found.isPresent(),
                found.map(UserSession::getRevokedAt).orElse(null),
                found.map(UserSession::getExpiresAt).orElse(null),
                properties.isAllowUntrackedTokens(),
                issuedSeconds,
                user.getTokensValidAfter(),
                Instant.now());

        return new TokenCheck(verdict, found.orElse(null));
    }

    public void touch(UserSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        Instant now = Instant.now();
        if (session.getLastSeenAt() != null && Duration.between(session.getLastSeenAt(), now).compareTo(TOUCH_INTERVAL) < 0) {
            return;
        }
        try {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(session.getId())),
                    new Update().set("lastSeenAt", now), UserSession.class);
        } catch (Exception e) {
            log.debug("Could not update session activity: {}", e.getClass().getSimpleName());
        }
    }

    public UserSession ensureTracked(User user, String rawToken) {
        String jti = jwtUtil.extractJti(rawToken);
        Optional<UserSession> existing = sessions.findByJti(jti);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        UserSession session = new UserSession();
        session.setJti(jti);
        session.setUserId(user.getId());
        session.setCreatedAt(jwtUtil.extractIssuedAt(rawToken) != null ? jwtUtil.extractIssuedAt(rawToken) : now);
        session.setLastSeenAt(now);
        session.setExpiresAt(jwtUtil.extractExpiration(rawToken));
        session.setDeviceLabel("Signed in before session tracking");
        return sessions.save(session);
    }

    public Optional<UserSession> findByJti(String jti) {
        return jti == null ? Optional.empty() : sessions.findByJti(jti);
    }

    public List<UserSession> listActive(String userId) {
        return sessions.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now());
    }

    public UserSession revoke(String userId, String sessionId, SessionRevokeReason reason) {
        UserSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "We couldn't find that session."));
        if (session.getRevokedAt() == null) {
            markRevoked(session, reason);
            audit.record(SecurityEventType.SESSION_REVOKED, userId, userId, "reason=" + reason.name());
        }
        return session;
    }

    public int revokeOthers(User user, String currentRawToken, SessionRevokeReason reason) {
        UserSession current = ensureTracked(user, currentRawToken);
        int count = revokeWhere(user.getId(), current.getJti(), reason);
        advanceCutoff(user.getId());
        audit.record(SecurityEventType.SESSIONS_REVOKED, user.getId(), user.getId(),
                "reason=" + reason.name() + ", count=" + count);
        return count;
    }

    public int revokeAll(String userId, SessionRevokeReason reason) {
        int count = revokeWhere(userId, null, reason);
        advanceCutoff(userId);
        audit.record(SecurityEventType.SESSIONS_REVOKED, userId, userId, "reason=" + reason.name() + ", count=" + count);
        return count;
    }

    public int revokeByDevice(String userId, String deviceId, SessionRevokeReason reason) {
        int count = 0;
        for (UserSession session : sessions.findByUserIdAndDeviceIdAndRevokedAtIsNull(userId, deviceId)) {
            markRevoked(session, reason);
            count++;
        }
        return count;
    }

    public void revokeByJti(String jti, SessionRevokeReason reason) {
        if (jti == null) {
            return;
        }
        sessions.findByJti(jti).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                markRevoked(session, reason);
            }
        });
    }

    public void cutOffUntrackedTokens(String userId) {
        advanceCutoff(userId);
    }

    private int revokeWhere(String userId, String exceptJti, SessionRevokeReason reason) {
        int count = 0;
        for (UserSession session : listActive(userId)) {
            if (exceptJti != null && exceptJti.equals(session.getJti())) {
                continue;
            }
            markRevoked(session, reason);
            count++;
        }
        return count;
    }

    private void markRevoked(UserSession session, SessionRevokeReason reason) {
        session.setRevokedAt(Instant.now());
        session.setRevokeReason(reason.name());
        sessions.save(session);
        blacklist.revokeJti(session.getJti(), session.getExpiresAt());
    }

    private void advanceCutoff(String userId) {
        long cutoff = Instant.now().getEpochSecond() + 1;
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(userId)),
                new Update().set("tokensValidAfter", cutoff), User.class);
    }
}
