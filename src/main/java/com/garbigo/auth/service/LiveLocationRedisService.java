package com.garbigo.auth.service;

import com.garbigo.auth.model.LiveLocation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LiveLocationRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LIVE_LOCATION_PREFIX = "live:location:";
    private static final Duration TTL = Duration.ofHours(2);

    public LiveLocationRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateLiveLocation(String userId, double latitude, double longitude) {
        LiveLocation location = new LiveLocation();
        location.setUserId(userId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);

        String key = LIVE_LOCATION_PREFIX + userId;
        System.out.println("Saving to Redis → Key: " + key);

        redisTemplate.opsForValue().set(key, location, TTL);
    }

    public LiveLocation getCurrentLocation(String userId) {
        String key = LIVE_LOCATION_PREFIX + userId;
        System.out.println("Fetching from Redis → Key: " + key);

        Object obj = redisTemplate.opsForValue().get(key);

        if (obj == null) {
            System.out.println("No data in Redis for: " + key);
            return null;
        }

        if (obj instanceof LiveLocation liveLocation) {
            System.out.println("Successfully retrieved LiveLocation from Redis");
            return liveLocation;
        } else {
            System.out.println("Wrong type received: " + obj.getClass().getName());
            return null;
        }
    }
}