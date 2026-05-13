package com.garbigo.auth.service;

import com.garbigo.auth.model.LiveLocation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

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
        // We intentionally don't set id and timestamp here for Redis

        String key = LIVE_LOCATION_PREFIX + userId;
        System.out.println("Saving to Redis → Key: " + key);

        redisTemplate.opsForValue().set(key, location, TTL);
    }

    public LiveLocation getCurrentLocation(String userId) {
        String key = LIVE_LOCATION_PREFIX + userId;
        System.out.println("Fetching from Redis → Key: " + key);

        Object obj = redisTemplate.opsForValue().get(key);

        if (obj == null) {
            System.out.println("No live location in Redis");
            return null;
        }

        if (obj instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;

                LiveLocation location = new LiveLocation();
                location.setUserId((String) map.get("userId"));
                
                Object lat = map.get("latitude");
                Object lng = map.get("longitude");

                if (lat != null) location.setLatitude(Double.parseDouble(lat.toString()));
                if (lng != null) location.setLongitude(Double.parseDouble(lng.toString()));

                // Set current timestamp for display
                location.setTimestamp(java.time.Instant.now());

                System.out.println("Successfully restored LiveLocation from Redis");
                return location;

            } catch (Exception e) {
                System.out.println("Conversion error: " + e.getMessage());
                return null;
            }
        }

        if (obj instanceof LiveLocation) {
            return (LiveLocation) obj;
        }

        return null;
    }
}