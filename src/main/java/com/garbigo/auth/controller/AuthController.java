package com.garbigo.auth.controller;

import com.garbigo.auth.dto.*;
import com.garbigo.auth.security.ClientContext;
import com.garbigo.auth.security.ClientContextFactory;
import com.garbigo.auth.service.AuthService;
import com.garbigo.auth.service.SocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String RESET_MESSAGE =
            "If an account exists for that email, we have sent a password reset link to it.";
    private static final String RESEND_MESSAGE =
            "If that email belongs to an account that still needs verifying, we have sent a new link to it.";

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final ClientContextFactory contexts;

    public AuthController(AuthService authService, SocialAuthService socialAuthService,
                          ClientContextFactory contexts) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.contexts = contexts;
    }

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody AuthRequest request,
                                               HttpServletRequest http, HttpServletResponse response) {
        ClientContext ctx = contexts.from(http);
        return respond(authService.signin(request, ctx), response);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
        authService.logout(extractBearerToken(request));
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
        return ResponseEntity.ok(new MessageResponse(authService.verifyAccount(token)));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody PasswordResetRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(RESEND_MESSAGE));
    }

    @PostMapping("/reset-password/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(RESET_MESSAGE));
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
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody SocialLoginRequest request,
                                                    HttpServletRequest http, HttpServletResponse response) {
        return respond(socialAuthService.googleLogin(request, contexts.from(http)), response);
    }

    @PostMapping("/social/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody SocialLoginRequest request,
                                                      HttpServletRequest http, HttpServletResponse response) {
        return respond(socialAuthService.facebookLogin(request, contexts.from(http)), response);
    }

    @PostMapping("/social/github")
    public ResponseEntity<AuthResponse> githubLogin(@Valid @RequestBody SocialLoginRequest request,
                                                    HttpServletRequest http, HttpServletResponse response) {
        return respond(socialAuthService.githubLogin(request, contexts.from(http)), response);
    }

    private ResponseEntity<AuthResponse> respond(SignInResult result, HttpServletResponse response) {
        contexts.attachDeviceToken(response, result.newDeviceToken());
        return ResponseEntity.ok(result.auth());
    }
}
