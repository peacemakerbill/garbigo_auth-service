package com.garbigo.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "Welcome to Garbigo 🚛\n\n" +
               "A Smart Garbage SAAS Platform\n\n" +
               "Connecting Clients and Waste Collectors Efficiently.\n\n" +
               "Status: ✅ Running Successfully";
    }

    @GetMapping("/health")
    public String health() {
        return "Garbigo Auth Service is running successfully! ✅";
    }
}