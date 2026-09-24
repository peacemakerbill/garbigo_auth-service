package com.garbigo.auth.controller;

import com.garbigo.auth.dto.*;
import com.garbigo.auth.security.TokenBlacklistService;
import com.garbigo.auth.service.AuthService;
import com.garbigo.auth.service.SocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.signin(request));
    }

    // Requires a valid (not-yet-revoked, not-expired) token to reach this point at
    // all - SecurityConfig doesn't list this as public, so JwtFilter already ran and
    // authenticated the request before this method is invoked.
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
    public ResponseEntity<String> verify(@RequestParam String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok("Account verified successfully");
    }

    // Endpoint for Resending Verification Email
    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@RequestBody AuthRequest request) {
        try {
            authService.resendVerificationEmail(request.getEmail());
            return ResponseEntity.ok("Verification email resent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to resend verification: " + e.getMessage());
        }
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

    // ====================== SOCIAL SIGN UP / SIGN IN ======================
    // Each endpoint is create-or-login in one step: an unrecognized email creates a new
    // (pre-verified) account, a recognized one just logs in. That's why there's no separate
    // "social signup" endpoint - sign up and sign in are the same request for social auth.

    // request.token = the Google ID token from Google Sign-In on the client.
    @PostMapping("/social/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.googleLogin(request));
    }

    // request.token = the Facebook access token from the Facebook SDK on the client.
    @PostMapping("/social/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(@RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.facebookLogin(request));
    }

    // request.token = the OAuth "code" GitHub redirects back with after the user approves
    // access - NOT an access token itself. See SocialAuthService.githubLogin for why.
    @PostMapping("/social/github")
    public ResponseEntity<AuthResponse> githubLogin(@RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.githubLogin(request));
    }
}