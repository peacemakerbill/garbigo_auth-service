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
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody AuthRequest request) {
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

    // Only ever reads the email - PasswordResetRequest fits exactly (was previously
    // AuthRequest, which would have wrongly required an unused password field once
    // @Valid was added).
    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@Valid @RequestBody PasswordResetRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok("Verification email resent successfully");
    }

    @PostMapping("/reset-password/request")
    public ResponseEntity<String> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok("Password reset link sent to email");
    }

    // PasswordResetConfirmRequest, not ChangePasswordRequest: this flow (reset via
    // emailed token) has no "old password" to check, unlike the authenticated
    // change-password endpoint below.
    @PostMapping("/reset-password/confirm")
    public ResponseEntity<String> confirmPasswordReset(@RequestParam String token,
                                                         @Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(token, request.getNewPassword());
        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }

    // ====================== SOCIAL SIGN UP / SIGN IN ======================
    // Each endpoint is create-or-login in one step: an unrecognized email creates a new
    // (pre-verified) account, a recognized one just logs in. That's why there's no separate
    // "social signup" endpoint - sign up and sign in are the same request for social auth.

    // request.token = the Google ID token from Google Sign-In on the client.
    @PostMapping("/social/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.googleLogin(request));
    }

    // request.token = the Facebook access token from the Facebook SDK on the client.
    @PostMapping("/social/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.facebookLogin(request));
    }

    // request.token = the OAuth "code" GitHub redirects back with after the user approves
    // access - NOT an access token itself. See SocialAuthService.githubLogin for why.
    @PostMapping("/social/github")
    public ResponseEntity<AuthResponse> githubLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.githubLogin(request));
    }
}