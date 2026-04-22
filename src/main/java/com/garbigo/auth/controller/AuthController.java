package com.garbigo.auth.controller;

import com.garbigo.auth.dto.*;
import com.garbigo.auth.service.AuthService;
import com.garbigo.auth.service.SocialAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;

    public AuthController(AuthService authService, SocialAuthService socialAuthService) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.signin(request));
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verify(@RequestParam String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok("Account verified successfully");
    }

    // Endpoint for Resending Verification Email
    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@RequestBody AuthRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok("Verification email resent");
    }

    @PostMapping("/reset-password/request")
    public ResponseEntity<String> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok("Password reset link sent to email");
    }

    @PostMapping("/reset-password/confirm")
    public ResponseEntity<String> confirmPasswordReset(@RequestParam String token, @RequestBody ChangePasswordRequest request) {
        authService.resetPassword(token, request.getNewPassword());
        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PostMapping("/social/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.googleLogin(request));
    }

    @PostMapping("/social/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(@RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.facebookLogin(request));
    }

    @PostMapping("/social/apple")
    public ResponseEntity<AuthResponse> appleLogin(@RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.appleLogin(request));
    }
}