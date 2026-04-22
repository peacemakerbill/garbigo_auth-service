package com.garbigo.auth.util;

import com.garbigo.auth.config.RateLimiterConfig.RateLimitProperties;
import com.garbigo.auth.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import org.redisson.api.RMapCache;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimiter {

    private final RMapCache<String, Integer> rateLimitCache;
    private final RateLimitProperties rateLimitProperties;
    private final HttpServletRequest request;

    public RateLimiter(RMapCache<String, Integer> rateLimitCache,
                       RateLimitProperties rateLimitProperties,
                       HttpServletRequest request) {
        this.rateLimitCache = rateLimitCache;
        this.rateLimitProperties = rateLimitProperties;
        this.request = request;
    }

    public void checkRateLimit() {
        String key = getClientIdentifier();
        
        // Use Redisson's atomic operations for thread safety
        Integer currentCount = rateLimitCache.get(key);
        
        if (currentCount == null) {
            // First request in the window
            rateLimitCache.put(key, 1, rateLimitProperties.windowInSeconds(), TimeUnit.SECONDS);
        } else if (currentCount >= rateLimitProperties.requestsPerMinute()) {
            throw new CustomException("Too many requests. Please try again in " + 
                                     rateLimitProperties.windowInSeconds() + " seconds.");
        } else {
            // Increment the count
            rateLimitCache.put(key, currentCount + 1, rateLimitProperties.windowInSeconds(), TimeUnit.SECONDS);
        }
    }

    private String getClientIdentifier() {
        // Try to get IP from request headers (for proxy environments)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // For multiple IPs (behind proxy), take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return "rate_limit:" + ip + ":" + System.currentTimeMillis() / (rateLimitProperties.windowInSeconds() * 1000);
    }
}