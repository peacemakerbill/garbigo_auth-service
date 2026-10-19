package com.garbigo.auth.service;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.util.ClientIpResolver;
import com.garbigo.auth.util.GeoLocation;
import com.garbigo.auth.util.IpInfoParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GeoLocationService {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int CACHE_SIZE = 1000;

    private record CacheEntry(GeoLocation location, Instant storedAt) {
    }

    private final SecurityProperties properties;
    private final HttpClient client;
    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public GeoLocationService(SecurityProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getIpGeolocationTimeoutMs())))
                .build();
    }

    public GeoLocation lookup(String ip) {
        if (!properties.isIpGeolocationEnabled() || !ClientIpResolver.isPublicAddress(ip)) {
            return GeoLocation.UNKNOWN;
        }

        CacheEntry cached = cache.get(ip);
        if (cached != null && cached.storedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.location();
        }

        try {
            String url = properties.getIpGeolocationUrl().replace("{ip}", URLEncoder.encode(ip, StandardCharsets.UTF_8));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getIpGeolocationTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET();
            if (properties.getIpGeolocationToken() != null && !properties.getIpGeolocationToken().isBlank()) {
                request.header("Authorization", "Bearer " + properties.getIpGeolocationToken().trim());
            }

            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("Location lookup returned status {}", response.statusCode());
                return GeoLocation.UNKNOWN;
            }

            GeoLocation location = IpInfoParser.parse(response.body());
            cache.put(ip, new CacheEntry(location, Instant.now()));
            return location;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GeoLocation.UNKNOWN;
        } catch (Exception e) {
            log.debug("Location lookup failed: {}", e.getClass().getSimpleName());
            return GeoLocation.UNKNOWN;
        }
    }
}
