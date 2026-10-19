package com.garbigo.auth.config;

import com.garbigo.auth.repository.SecurityActionTokenRepository;
import com.garbigo.auth.repository.SecurityEventRepository;
import com.garbigo.auth.repository.SignInRecordRepository;
import com.garbigo.auth.repository.UserDeviceRepository;
import com.garbigo.auth.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class SecurityRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(SecurityRetentionJob.class);

    private final SecurityEventRepository events;
    private final SignInRecordRepository signIns;
    private final UserDeviceRepository devices;
    private final UserSessionRepository sessions;
    private final SecurityActionTokenRepository actionTokens;
    private final SecurityProperties properties;

    public SecurityRetentionJob(SecurityEventRepository events, SignInRecordRepository signIns,
                                UserDeviceRepository devices, UserSessionRepository sessions,
                                SecurityActionTokenRepository actionTokens, SecurityProperties properties) {
        this.events = events;
        this.signIns = signIns;
        this.devices = devices;
        this.sessions = sessions;
        this.actionTokens = actionTokens;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT${app.security.retention-interval-minutes:360}M")
    public void purge() {
        try {
            Instant now = Instant.now();
            Instant cutoff = now.minus(Duration.ofDays(Math.max(1, properties.getAuditRetentionDays())));
            Instant sessionCutoff = now.minus(Duration.ofDays(Math.min(30, Math.max(1, properties.getAuditRetentionDays()))));

            long removedEvents = events.deleteByOccurredAtBefore(cutoff);
            long removedSignIns = signIns.deleteByOccurredAtBefore(cutoff);
            long removedDevices = devices.deleteByLastSeenAtBefore(cutoff);
            long removedSessions = sessions.deleteByExpiresAtBefore(sessionCutoff);
            long removedTokens = actionTokens.deleteByExpiresAtBefore(now.minus(Duration.ofDays(1)));

            log.info("Security data cleanup finished: events={}, signIns={}, devices={}, sessions={}, actionTokens={}",
                    removedEvents, removedSignIns, removedDevices, removedSessions, removedTokens);
        } catch (Exception e) {
            log.warn("Security data cleanup failed: {}", e.getClass().getSimpleName());
        }
    }
}
