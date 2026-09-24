package com.garbigo.auth.security;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWTs are stateless by design - a signed token can't actually be "cancelled" once
 * issued, it just remains self-verifying and valid until its own exp claim passes.
 * To support a real logout, this maintains a Redis-backed denylist of revoked jti
 * (JWT ID) values.
 * <p>
 * Each entry is keyed with a TTL equal to the token's own remaining validity at the
 * moment of revocation - so a revoked entry never needs manual cleanup, and never
 * outlives the token it revoked anyway (once the token would have expired on its
 * own, there's nothing left to revoke).
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "revoked:jti:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;

    public TokenBlacklistService(RedisTemplate<String, Object> redisTemplate, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Revokes the given token. After this, isRevoked(its jti) returns true for as
     * long as the token would otherwise still have been valid.
     */
    public void revoke(String token) {
        String jti = jwtUtil.extractJti(token);
        if (jti == null) {
            return; // token predates the jti claim - nothing to key the revocation on
        }

        Duration remaining = Duration.between(Instant.now(), jwtUtil.extractExpiration(token));
        if (remaining.isNegative() || remaining.isZero()) {
            return; // already expired on its own
        }

        redisTemplate.opsForValue().set(KEY_PREFIX + jti, Boolean.TRUE, remaining);
    }

    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}