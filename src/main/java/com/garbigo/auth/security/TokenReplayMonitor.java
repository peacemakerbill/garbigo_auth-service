package com.garbigo.auth.security;

import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.service.SecurityAuditService;
import com.garbigo.auth.service.SecurityNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TokenReplayMonitor {

    private static final Logger log = LoggerFactory.getLogger(TokenReplayMonitor.class);

    static final int ALERT_AFTER_ATTEMPTS = 3;

    private final RedisTemplate<String, Object> redis;
    private final SecurityAuditService audit;
    private final ObjectProvider<SecurityNotificationService> notifications;

    public TokenReplayMonitor(RedisTemplate<String, Object> redis, SecurityAuditService audit,
                              ObjectProvider<SecurityNotificationService> notifications) {
        this.redis = redis;
        this.audit = audit;
        this.notifications = notifications;
    }

    public void report(String jti, String userId, String ip) {
        if (jti == null || userId == null) {
            return;
        }
        try {
            Boolean first = redis.opsForValue().setIfAbsent("replay:seen:" + jti, Boolean.TRUE, Duration.ofHours(1));
            if (!Boolean.TRUE.equals(first)) {
                return;
            }

            audit.recordWithIp(SecurityEventType.TOKEN_REPLAY_ATTEMPT, userId, null, ip,
                    "A token that was already signed out was used again");

            String countKey = "replay:count:" + userId;
            Long count = redis.opsForValue().increment(countKey);
            if (count != null && count == 1L) {
                redis.expire(countKey, Duration.ofHours(1));
            }

            if (count != null && count >= ALERT_AFTER_ATTEMPTS) {
                Boolean alertDue = redis.opsForValue().setIfAbsent("replay:alert:" + userId, Boolean.TRUE,
                        Duration.ofHours(6));
                SecurityNotificationService notifier = notifications.getIfAvailable();
                if (Boolean.TRUE.equals(alertDue) && notifier != null) {
                    notifier.notifyTokenReplay(userId, ip);
                }
            }
        } catch (Exception e) {
            log.debug("Could not record a token replay attempt: {}", e.getClass().getSimpleName());
        }
    }
}
