package com.garbigo.auth.dto;

import lombok.Data;

@Data
public class PasswordResetRequest {
    private String email;
}