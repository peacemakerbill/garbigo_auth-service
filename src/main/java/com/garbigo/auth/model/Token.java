package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "tokens")
public class Token {
    private String id;
    private String userId;
    private String token;
    private String type; // VERIFICATION or RESET
    private long expiry;

    @CreatedDate
    private LocalDateTime createdAt;
}