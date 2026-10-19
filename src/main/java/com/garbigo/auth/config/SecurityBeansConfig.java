package com.garbigo.auth.config;

import com.garbigo.auth.security.AdminEmailPolicy;
import com.garbigo.auth.security.AttemptStore;
import com.garbigo.auth.security.LoginThrottle;
import com.garbigo.auth.security.RedisAttemptStore;
import com.garbigo.auth.util.ClientIpResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, AdminSeedProperties.class})
public class SecurityBeansConfig {

    @Bean
    AdminEmailPolicy adminEmailPolicy(SecurityProperties properties, AdminSeedProperties seedProperties) {
        String initial = properties.getInitialAdminEmails();
        String seeded = seedProperties.seededEmails();
        if (!seeded.isEmpty()) {
            initial = initial == null || initial.isBlank() ? seeded : initial + "," + seeded;
        }
        return new AdminEmailPolicy(initial, properties.getAutoAdminEmails());
    }

    @Bean
    ClientIpResolver clientIpResolver(SecurityProperties properties) {
        return new ClientIpResolver(properties.getTrustedProxies());
    }

    @Bean
    AttemptStore attemptStore(RedisTemplate<String, Object> redisTemplate) {
        return new RedisAttemptStore(redisTemplate);
    }

    @Bean
    LoginThrottle loginThrottle(AttemptStore attemptStore, SecurityProperties properties) {
        return new LoginThrottle(attemptStore, new LoginThrottle.Config(
                properties.getMaxFailedLoginsPerEmailAndIp(),
                properties.getMaxFailedLoginsPerIp(),
                properties.getMaxFailedLoginsPerEmail(),
                Duration.ofMinutes(properties.getFailureWindowMinutes()),
                Duration.ofMinutes(properties.getLockoutMinutes()),
                Duration.ofMinutes(properties.getMaxLockoutMinutes())));
    }
}
