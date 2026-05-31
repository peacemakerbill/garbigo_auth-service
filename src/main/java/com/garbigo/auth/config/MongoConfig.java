package com.garbigo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.time.Instant;
import java.util.Optional;

/**
 * MongoConfig enables:
 *   - @CreatedDate / @LastModifiedDate auto-population on all MongoDB documents
 *   - Repository scanning scoped to the correct base package
 *
 * Without @EnableMongoAuditing, @CreatedDate on ProfileView.viewedAt will
 * never be set automatically and will always be null.
 */
@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@EnableMongoRepositories(basePackages = "com.garbigo.auth.repository")
public class MongoConfig {

    /**
     * Provides Instant-compatible timestamps for @CreatedDate / @LastModifiedDate.
     *
     * Spring Data MongoDB's default auditing uses Date; this bean tells it to
     * use java.time.Instant instead, which matches the field type on ProfileView.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}