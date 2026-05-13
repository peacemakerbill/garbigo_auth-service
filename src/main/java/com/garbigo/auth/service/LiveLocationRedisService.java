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

        String key = LIVE_LOCATION_PREFIX + userId;
        System.out.println("Saving to Redis → Key: " + key);

        redisTemplate.opsForValue().set(key, location, TTL);
    }

    public LiveLocation getCurrentLocation(String userId) {
        String key = LIVE_LOCATION_PREFIX + userId;
        System.out.println("Fetching from Redis → Key: " + key);

        Object obj = redisTemplate.opsForValue().get(key);

        if (obj == null) {
            System.out.println("No data found in Redis for key: " + key);
            return null;
        }

        // Handle LinkedHashMap from Redis
        if (obj instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;

                LiveLocation location = new LiveLocation();
                
                location.setId((String) map.get("id"));
                location.setUserId((String) map.get("userId"));
                
                // Handle latitude and longitude safely
                if (map.get("latitude") != null) {
                    location.setLatitude(Double.parseDouble(map.get("latitude").toString()));
                }
                if (map.get("longitude") != null) {
                    location.setLongitude(Double.parseDouble(map.get("longitude").toString()));
                }

                // Handle timestamp
                if (map.get("timestamp") != null) {
                    // Try to parse timestamp if it's a number (epoch) or string
                    Object ts = map.get("timestamp");
                    if (ts instanceof Number) {
                        // You can set it if your model supports Long timestamp
                        System.out.println("Timestamp found: " + ts);
                    }
                }

                System.out.println("Successfully converted LinkedHashMap to LiveLocation for user: " + userId);
                return location;

            } catch (Exception e) {
                System.out.println("Error converting Redis data: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        // If it's already the correct type
        if (obj instanceof LiveLocation) {
            return (LiveLocation) obj;
        }

        System.out.println("Unknown type received: " + obj.getClass().getName());
        return null;
    }
}