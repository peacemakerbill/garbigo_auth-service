package com.garbigo.auth.controller;

import com.garbigo.auth.dto.*;
import com.garbigo.auth.security.TokenBlacklistService;
import com.garbigo.auth.service.AuthService;
import com.garbigo.auth.service.SocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(AuthService authService, SocialAuthService socialAuthService,
                           TokenBlacklistService tokenBlacklistService) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.signin(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token != null) {
            tokenBlacklistService.revoke(token);
        }
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    @GetMapping("/verify")
    public ResponseEntity<MessageResponse> verify(@RequestParam String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok(new MessageResponse("Account verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody PasswordResetRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("Verification email resent successfully"));
    }

    @PostMapping("/reset-password/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("Password reset link sent to email"));
    }

    @PostMapping("/reset-password/confirm")
    public ResponseEntity<MessageResponse> confirmPasswordReset(@RequestParam String token,
                                                                  @Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(token, request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    @PostMapping("/social/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.googleLogin(request));
    }

    @PostMapping("/social/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.facebookLogin(request));
    }

    @PostMapping("/social/github")
    public ResponseEntity<AuthResponse> githubLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.githubLogin(request));
    }
}