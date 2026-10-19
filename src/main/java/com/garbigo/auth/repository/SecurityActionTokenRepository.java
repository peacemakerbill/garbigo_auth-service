package com.garbigo.auth.repository;

import com.garbigo.auth.model.SecurityActionToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface SecurityActionTokenRepository extends MongoRepository<SecurityActionToken, String> {

    Optional<SecurityActionToken> findByTokenHash(String tokenHash);

    long deleteByExpiresAtBefore(Instant cutoff);
}
