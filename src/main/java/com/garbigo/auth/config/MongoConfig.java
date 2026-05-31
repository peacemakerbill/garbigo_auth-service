package com.garbigo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.time.Instant;
import java.util.Optional;

/**
 * Central MongoDB configuration.
 *
 * Keeping auditing here (rather than on the main application class) makes it
 * easy to add further Mongo-specific settings (converters, connection options,
 * transaction managers, etc.) in one dedicated place as the project grows.
 */
@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@EnableMongoRepositories(basePackages = "com.garbigo.auth.repository")
public class MongoConfig {

    /**
     * Provides Instant-compatible timestamps for @CreatedDate / @LastModifiedDate.
     *
     * Spring Data MongoDB defaults to java.util.Date for auditing; this bean
     * overrides that so fields declared as java.time.Instant are populated correctly.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}