package com.garbigo.auth.repository;

import com.garbigo.auth.model.UserSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends MongoRepository<UserSession, String> {

    Optional<UserSession> findByJti(String jti);

    Optional<UserSession> findByIdAndUserId(String id, String userId);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(String userId, Instant now);

    List<UserSession> findByUserIdAndDeviceIdAndRevokedAtIsNull(String userId, String deviceId);

    long deleteByExpiresAtBefore(Instant cutoff);
}
