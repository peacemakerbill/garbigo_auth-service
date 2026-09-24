package com.garbigo.auth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * A dedicated Lettuce-backed connection factory, used only by the RedisTemplate
     * below - deliberately NOT the same RedisConnectionFactory redisson-spring-boot-starter
     * auto-configures for RedissonClient's own use (RMapCache in RateLimiterConfig).
     * <p>
     * Redisson's Spring Data Redis connector (RedissonConnection) is version-pinned to
     * a specific Spring Data Redis release, and as of this project's Spring Boot 4.1.1 /
     * Spring Data Redis 4.x pairing, routing plain RedisTemplate operations through it
     * causes a StackOverflowError - a command isn't properly overridden by Redisson's
     * connector, and Spring Data Redis's default method dispatch (DefaultedRedisConnection)
     * ends up recursing into itself. Lettuce ships as part of spring-data-redis itself,
     * so it's always version-aligned with whatever Spring Data Redis version is on the
     * classpath - no cross-project version skew possible.
     */
    @Bean
    @Qualifier("lettuceConnectionFactory")
    LettuceConnectionFactory lettuceConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
    }

    @Bean
    RedisTemplate<String, Object> redisTemplate(
            @Qualifier("lettuceConnectionFactory") LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Spring Boot 4 defaults to Jackson 3. Jackson2JsonRedisSerializer is deprecated
        // in favor of JacksonJsonRedisSerializer, which is built on Jackson 3's immutable
        // JsonMapper instead of the old mutable ObjectMapper.
        //
        // No JavaTimeModule registration needed here: as of Jackson 3, java.time (JSR-310)
        // support ships built into jackson-databind, so Instant/LocalDateTime fields
        // (e.g. LiveLocation.timestamp) serialize correctly out of the box.
        JsonMapper jsonMapper = JsonMapper.builder().build();

        JacksonJsonRedisSerializer<Object> serializer =
                new JacksonJsonRedisSerializer<>(jsonMapper, Object.class);

        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serializer
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}