package com.garbigo.auth.service;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.exception.ApiException;
import com.garbigo.auth.model.User;
import com.garbigo.auth.model.UserSession;
import com.garbigo.auth.security.ClientContext;
import com.garbigo.auth.security.LoginThrottle;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class ReauthService {

    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle throttle;
    private final SecurityProperties properties;

    public ReauthService(PasswordEncoder passwordEncoder, LoginThrottle throttle, SecurityProperties properties) {
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.properties = properties;
    }

    public void require(User user, UserSession currentSession, String password, ClientContext ctx) {
        String key = SignInSecurityService.emailKey(user.getEmail());

        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            LoginThrottle.Status status = throttle.check(key, ctx.ip());
            if (status.locked()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many attempts. Please wait a few minutes and try again.",
                        status.retryAfter().toSeconds());
            }

            if (password == null || password.isBlank()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Please enter your password to confirm this change.");
            }

            if (!passwordEncoder.matches(password, user.getPassword())) {
                throttle.recordFailure(key, ctx.ip());
                throw new ApiException(HttpStatus.FORBIDDEN, "That password isn't correct.");
            }
            return;
        }

        Duration window = Duration.ofMinutes(properties.getRecentAuthMinutes());
        boolean recent = currentSession != null && currentSession.getCreatedAt() != null
                && currentSession.getCreatedAt().isAfter(Instant.now().minus(window));
        if (!recent) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "For your protection, please sign in again before making this change.");
        }
    }
}
