package com.garbigo.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class SecurityAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityAsyncConfig.class);

    @Bean(name = "securityTaskExecutor")
    Executor securityTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("security-async-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("A security notification was dropped because the queue is full."));
        executor.initialize();
        return executor;
    }
}
