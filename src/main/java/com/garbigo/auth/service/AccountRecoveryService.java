package com.garbigo.auth.service;

import com.garbigo.auth.model.Token;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AccountRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);

    private static final long VERIFICATION_VALID_MILLIS = 24L * 60 * 60 * 1000;
    private static final long RESET_VALID_MILLIS = 60L * 60 * 1000;

    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final TokenRepository tokenRepository;
    private final EmailService emailService;

    public AccountRecoveryService(TokenRepository tokenRepository, EmailService emailService) {
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    public boolean sendVerificationEmail(User user) {
        if (issuedRecently(user, "VERIFICATION")) {
            return false;
        }
        tokenRepository.deleteByUserIdAndType(user.getId(), "VERIFICATION");

        String raw = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + VERIFICATION_VALID_MILLIS;
        save(user, raw, "VERIFICATION", expiry);

        String email = user.getEmail();
        String name = displayName(user);
        Instant expiresAt = Instant.ofEpochMilli(expiry);
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendVerificationEmail(email, name, raw, expiresAt);
            } catch (Exception e) {
                log.warn("Could not send a verification email: {}", e.getClass().getSimpleName());
            }
        });
        return true;
    }

    public boolean sendPasswordResetEmail(User user) {
        if (issuedRecently(user, "RESET")) {
            return false;
        }
        tokenRepository.deleteByUserIdAndType(user.getId(), "RESET");

        String raw = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + RESET_VALID_MILLIS;
        save(user, raw, "RESET", expiry);

        String email = user.getEmail();
        String name = displayName(user);
        Instant expiresAt = Instant.ofEpochMilli(expiry);
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendResetPasswordEmail(email, name, raw, expiresAt);
            } catch (Exception e) {
                log.warn("Could not send a password reset email: {}", e.getClass().getSimpleName());
            }
        });
        return true;
    }

    private boolean issuedRecently(User user, String type) {
        return tokenRepository.findFirstByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), type)
                .map(Token::getCreatedAt)
                .map(created -> created.isAfter(LocalDateTime.now().minus(RESEND_COOLDOWN)))
                .orElse(false);
    }

    private void save(User user, String raw, String type, long expiry) {
        Token token = new Token();
        token.setUserId(user.getId());
        token.setToken(raw);
        token.setType(type);
        token.setExpiry(expiry);
        tokenRepository.save(token);
    }

    private String displayName(User user) {
        return user.getFirstName() != null && !user.getFirstName().isBlank() ? user.getFirstName() : "there";
    }
}
