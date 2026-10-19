package com.garbigo.auth.security;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class RedisAttemptStore implements AttemptStore {

    private final RedisTemplate<String, Object> redis;

    public RedisAttemptStore(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttlIfNew) {
        Long value = redis.opsForValue().increment(key);
        long count = value == null ? 1 : value;
        if (count == 1) {
            redis.expire(key, ttlIfNew);
        } else {
            Long remaining = redis.getExpire(key);
            if (remaining != null && remaining == -1) {
                redis.expire(key, ttlIfNew);
            }
        }
        return count;
    }

    @Override
    public void lock(String key, Duration ttl) {
        redis.opsForValue().set(key, Boolean.TRUE, ttl);
    }

    @Override
    public Duration lockRemaining(String key) {
        Long millis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        if (millis == null || millis <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(millis);
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}
