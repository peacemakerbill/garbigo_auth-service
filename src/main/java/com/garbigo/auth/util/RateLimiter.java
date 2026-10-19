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
    private final ClientIpResolver ipResolver;

    public RateLimiter(RMapCache<String, Integer> rateLimitCache,
                       RateLimitProperties rateLimitProperties,
                       HttpServletRequest request, ClientIpResolver ipResolver) {
        this.rateLimitCache = rateLimitCache;
        this.rateLimitProperties = rateLimitProperties;
        this.request = request;
        this.ipResolver = ipResolver;
    }

    public void checkRateLimit() {
        String requestURI = request.getRequestURI();
        
        if (requestURI.contains("/live-location")) {
            return;
        }

        String key = getClientIdentifier();
        
        Integer currentCount = rateLimitCache.get(key);
        
        if (currentCount == null) {
            rateLimitCache.put(key, 1, rateLimitProperties.windowInSeconds(), TimeUnit.SECONDS);
        } else if (currentCount >= rateLimitProperties.requestsPerMinute()) {
            throw new CustomException("Too many requests. Please try again in " + 
                                     rateLimitProperties.windowInSeconds() + " seconds.");
        } else {
            rateLimitCache.put(key, currentCount + 1, rateLimitProperties.windowInSeconds(), TimeUnit.SECONDS);
        }
    }

    private String getClientIdentifier() {
        String ip = ipResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        return "rate_limit:" + ip + ":" + System.currentTimeMillis() / (rateLimitProperties.windowInSeconds() * 1000);
    }
}
