package com.garbigo.auth.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queue.user-created}")
    private String userCreatedQueue;

    @Bean
    public Queue userCreatedQueue() {
        return new Queue(userCreatedQueue, true);
    }
}