//package com.garbigo.auth.config;
//
//import feign.RequestInterceptor;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.core.context.SecurityContextHolder;
//
//@Configuration
//public class FeignConfig {
//
//    @Bean
//    public RequestInterceptor requestInterceptor() {
//        return requestTemplate -> {
//            var auth = SecurityContextHolder.getContext().getAuthentication();
//            if (auth != null && auth.getCredentials() instanceof String token) {
//                requestTemplate.header("Authorization", "Bearer " + token);
//            }
//        };
//    }
//}