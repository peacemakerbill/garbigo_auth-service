package com.garbigo.auth.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String role;
    private boolean verified;
}