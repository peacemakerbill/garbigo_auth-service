package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private Instant expiresAt;
}