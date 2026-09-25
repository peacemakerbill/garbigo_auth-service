package com.garbigo.auth.security;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "revoked:jti:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;

    public TokenBlacklistService(RedisTemplate<String, Object> redisTemplate, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    public void revoke(String token) {
        String jti = jwtUtil.extractJti(token);
        if (jti == null) {
            return;
        }

        Duration remaining = Duration.between(Instant.now(), jwtUtil.extractExpiration(token));
        if (remaining.isNegative() || remaining.isZero()) {
            return;
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