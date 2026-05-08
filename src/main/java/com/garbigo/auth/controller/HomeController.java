package com.garbigo.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");

        if (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_HTML_VALUE)) {
            return getHtmlResponse();
        }
        return getPlainTextResponse();
    }

    @GetMapping("/health")
    public String health() {
        return "Garbigo Auth Service is running successfully! ✅";
    }

    private String getHtmlResponse() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Garbigo</title>
                    <style>
                        body { font-family: Arial, sans-serif; text-align: center; margin-top: 100px; }
                        h1 { color: #2c7a7b; }
                        .status { color: #38a169; font-size: 1.3rem; }
                    </style>
                </head>
                <body>
                    <h1>Welcome to Garbigo 🚛</h1>
                    <h3>A Smart Garbage SAAS Platform</h3>
                    <p>Connecting Clients and Waste Collectors Efficiently.</p>
                    <p class="status">✅ Status: Running Successfully</p>
                </body>
                </html>
                """;
    }

    private String getPlainTextResponse() {
        return "Welcome to Garbigo 🚛\n\n" +
               "A Smart Garbage SAAS Platform\n\n" +
               "Connecting Clients and Waste Collectors Efficiently.\n\n" +
               "Status: ✅ Running Successfully";
    }
}