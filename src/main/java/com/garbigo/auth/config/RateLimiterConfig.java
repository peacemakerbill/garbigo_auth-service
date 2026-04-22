package com.garbigo.auth.config;

import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimiterConfig {

    @Value("${rate.limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${rate.limit.window-in-seconds:60}")
    private int windowInSeconds;

    @Bean
    public RMapCache<String, Integer> rateLimitCache(RedissonClient redissonClient) {
        return redissonClient.getMapCache("rate_limiter_cache");
    }

    @Bean
    public RateLimitProperties rateLimitProperties() {
        return new RateLimitProperties(requestsPerMinute, windowInSeconds);
    }

    public record RateLimitProperties(int requestsPerMinute, int windowInSeconds) {}
}