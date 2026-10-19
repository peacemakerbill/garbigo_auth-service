package com.garbigo.auth.service;

import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.model.SecurityEvent;
import com.garbigo.auth.repository.SecurityEventRepository;
import com.garbigo.auth.security.ClientContextFactory;
import com.garbigo.auth.util.EmailAddresses;
import com.garbigo.auth.util.SafeText;
import com.garbigo.auth.util.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final int MAX_DETAIL = 300;

    private final SecurityEventRepository events;
    private final ClientContextFactory contexts;

    public SecurityAuditService(SecurityEventRepository events, ClientContextFactory contexts) {
        this.events = events;
        this.contexts = contexts;
    }

    public void record(SecurityEventType type, String userId, String actorId, String detail) {
        write(type, userId, actorId, null, contexts.current().ip(), detail);
    }

    public void recordWithIp(SecurityEventType type, String userId, String actorId, String ip, String detail) {
        write(type, userId, actorId, null, ip, detail);
    }

    public void recordForEmail(SecurityEventType type, String email, String detail) {
        write(type, null, null, subjectHash(email), null, detail);
    }

    public boolean happenedRecentlyForEmail(SecurityEventType type, String email, Duration within) {
        try {
            return events.existsByTypeAndSubjectHashAndOccurredAtAfter(type, subjectHash(email),
                    Instant.now().minus(within));
        } catch (Exception e) {
            log.warn("Could not check recent security events: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public boolean happenedRecentlyForUser(SecurityEventType type, String userId, Duration within) {
        try {
            return events.existsByTypeAndUserIdAndOccurredAtAfter(type, userId, Instant.now().minus(within));
        } catch (Exception e) {
            log.warn("Could not check recent security events: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public static String subjectHash(String email) {
        String normalized = EmailAddresses.normalize(email);
        return normalized == null ? null : SecureTokens.sha256Hex(normalized).substring(0, 32);
    }

    private void write(SecurityEventType type, String userId, String actorId, String subjectHash, String ip,
                       String detail) {
        try {
            SecurityEvent event = new SecurityEvent();
            event.setType(type);
            event.setUserId(userId);
            event.setActorId(actorId);
            event.setSubjectHash(subjectHash);
            event.setIp(ip);
            event.setDetail(SafeText.clean(detail, MAX_DETAIL));
            event.setOccurredAt(Instant.now());
            events.save(event);
        } catch (Exception e) {
            log.warn("Could not record security event {}: {}", type, e.getClass().getSimpleName());
        }
    }
}
