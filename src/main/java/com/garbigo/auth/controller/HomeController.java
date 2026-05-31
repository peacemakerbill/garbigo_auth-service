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
        return "Garbigo Auth Service is running successfully.";
    }

    private String getHtmlResponse() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Garbigo Auth Service</title>
                    <style>
                        body {
                            font-family: monospace, Arial, sans-serif;
                            text-align: center;
                            margin-top: 60px;
                            background: #f8fafc;
                            color: #1e2937;
                        }
                        h1 {
                            color: #2c7a7b;
                            font-size: 2.8rem;
                            margin-bottom: 10px;
                        }
                        .subtitle {
                            color: #334155;
                            font-size: 1.4rem;
                            margin-bottom: 40px;
                        }
                        .status {
                            color: #166534;
                            font-size: 1.35rem;
                            font-weight: bold;
                            letter-spacing: 2px;
                        }
                        pre {
                            display: inline-block;
                            text-align: left;
                            margin: 30px 0;
                            font-size: 0.95rem;
                            line-height: 1.1;
                        }
                    </style>
                </head>
                <body>
                    <h1>Garbigo</h1>
                    <p class="subtitle">Smart Garbage SAAS Platform</p>
                    <pre>
   _____            _     _             
  |  __ \\          | |   (_)            
  | |  \\/ __ _ _ __| |__  _  __ _  ___  
  | | __ / _` | '__| '_ \\| |/ _` |/ _ \\ 
  | |_\\ \\ (_| | |  | |_) | | (_| | (_) |
   \\____/\\__,_|_|  |_.__/|_|\\__, |\\___/ 
                               __/ |     
                              |___/      
                    </pre>
                    <p class="status">AUTH SERVICE - RUNNING SUCCESSFULLY</p>
                    <div style="margin-top: 30px; color: #64748b;">
                        Connecting Clients and Waste Collectors Efficiently
                    </div>
                </body>
                </html>
                """;
    }

    private String getPlainTextResponse() {
        return """
                
                ============================================================
                                    GARBIGO
                ============================================================
                
                   _____            _     _             
                  |  __ \\          | |   (_)            
                  | |  \\/ __ _ _ __| |__  _  __ _  ___  
                  | | __ / _` | '__| '_ \\| |/ _` |/ _ \\ 
                  | |_\\ \\ (_| | |  | |_) | | (_| | (_) |
                   \\____/\\__,_|_|  |_.__/|_|\\__, |\\___/ 
                                              __/ |     
                                             |___/      
                
                ============================================================
                Smart Garbage SAAS Platform
                Connecting Clients and Waste Collectors Efficiently
                
                Status: RUNNING SUCCESSFULLY
                -----------------------------------------------------------
                Ready to handle authentication requests.
                ============================================================
                """;
    }
}