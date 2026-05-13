package com.garbigo.auth.service;

import com.garbigo.auth.model.LiveLocation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LiveLocationRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LIVE_LOCATION_PREFIX = "live:location:";
    private static final Duration TTL = Duration.ofHours(1);

    public LiveLocationRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateLiveLocation(String userId, double latitude, double longitude) {
        LiveLocation location = new LiveLocation();
        location.setUserId(userId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        // timestamp will be set by MongoDB

        String key = LIVE_LOCATION_PREFIX + userId;
        redisTemplate.opsForValue().set(key, location, TTL);
    }

    public LiveLocation getCurrentLocation(String userId) {
        String key = LIVE_LOCATION_PREFIX + userId;
        return (LiveLocation) redisTemplate.opsForValue().get(key);
    }
}