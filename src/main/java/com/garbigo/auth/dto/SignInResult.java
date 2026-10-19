package com.garbigo.auth.dto;

public record SignInResult(AuthResponse auth, String newDeviceToken) {
}
