package com.garbigo.auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.garbigo.auth.dto.*;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.Role;
import com.garbigo.auth.model.Token;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.TokenRepository;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.JwtUtil;
import com.garbigo.auth.util.RateLimiter;

import jakarta.mail.MessagingException;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final Cloudinary cloudinary;
    private final RabbitTemplate rabbitTemplate;
    private final RateLimiter rateLimiter;

    @Value("${rabbitmq.queue.user-created}")
    private String userCreatedQueue;

    public AuthService(UserRepository userRepository,
                       TokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil,
                       EmailService emailService,
                       Cloudinary cloudinary,
                       RabbitTemplate rabbitTemplate,
                       RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.cloudinary = cloudinary;
        this.rabbitTemplate = rabbitTemplate;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        try {
            rateLimiter.checkRateLimit();

            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new CustomException("Email already in use");
            }

            User user = new User();
            user.setUsername(request.getUsername());
            user.setFirstName(request.getFirstName());
            user.setMiddleName(request.getMiddleName());
            user.setLastName(request.getLastName());
            user.setEmail(request.getEmail());
            user.setPhoneNumber(request.getPhoneNumber());
            user.setHomeAddress(request.getHomeAddress());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole(request.getRole() != null ? request.getRole() : Role.CLIENT);

            user = userRepository.save(user);

            String verifyToken = UUID.randomUUID().toString();
            Token token = new Token();
            token.setUserId(user.getId());
            token.setToken(verifyToken);
            token.setType("VERIFICATION");
            token.setExpiry(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
            tokenRepository.save(token);

            sendEmailAndRabbitMQAsync(user, verifyToken);

            return buildAuthResponse(user);
            
        } catch (Exception e) {
            System.err.println("SIGNUP ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new CustomException("Signup failed: " + e.getMessage());
        }
    }

    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found"));
        
        if (user.isVerified()) {
            throw new CustomException("Account already verified");
        }
        
        String verifyToken = UUID.randomUUID().toString();
        Token token = new Token();
        token.setUserId(user.getId());
        token.setToken(verifyToken);
        token.setType("VERIFICATION");
        token.setExpiry(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
        tokenRepository.save(token);
        
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendVerificationEmail(user.getEmail(), verifyToken);
                System.out.println("Resent verification email to: " + user.getEmail());
            } catch (MessagingException e) {
                System.err.println("Failed to resend verification email: " + e.getMessage());
            }
        });
    }

    private void sendEmailAndRabbitMQAsync(User user, String verifyToken) {
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendVerificationEmail(user.getEmail(), verifyToken);
                System.out.println("Verification email sent to: " + user.getEmail());
            } catch (Exception e) {
                System.err.println("Failed to send verification email: " + e.getMessage());
            }
            
            try {
                rabbitTemplate.convertAndSend(userCreatedQueue, user);
                System.out.println("RabbitMQ message sent for user: " + user.getId());
            } catch (Exception e) {
                System.err.println("Failed to send RabbitMQ message: " + e.getMessage());
            }
        });
    }

    public AuthResponse signin(AuthRequest request) {
        try {
            rateLimiter.checkRateLimit();

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            User user = (User) authentication.getPrincipal();
            return buildAuthResponse(user);
            
        } catch (Exception e) {
            System.err.println("SIGNIN ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new CustomException("Login failed: " + e.getMessage());
        }
    }

    public void verifyAccount(String tokenStr) {
        try {
            Token token = tokenRepository.findByToken(tokenStr)
                    .orElseThrow(() -> new CustomException("Invalid verification token"));

            if (token.getExpiry() < System.currentTimeMillis()) {
                throw new CustomException("Verification token expired");
            }

            User user = userRepository.findById(token.getUserId())
                    .orElseThrow(() -> new CustomException("User not found"));

            user.setVerified(true);
            userRepository.save(user);
            tokenRepository.delete(token);
        } catch (Exception e) {
            System.err.println("VERIFY ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new CustomException("Verification failed: " + e.getMessage());
        }
    }

    public void requestPasswordReset(String email) {
        try {
            rateLimiter.checkRateLimit();

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomException("User not found"));

            String resetToken = UUID.randomUUID().toString();
            Token token = new Token();
            token.setUserId(user.getId());
            token.setToken(resetToken);
            token.setType("RESET");
            token.setExpiry(System.currentTimeMillis() + 60 * 60 * 1000);
            tokenRepository.save(token);

            CompletableFuture.runAsync(() -> {
                try {
                    emailService.sendResetPasswordEmail(email, resetToken);
                } catch (MessagingException e) {
                    System.err.println("Failed to send reset email: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("RESET PASSWORD ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new CustomException("Password reset request failed: " + e.getMessage());
        }
    }

    public void resetPassword(String tokenStr, String newPassword) {
        try {
            Token token = tokenRepository.findByToken(tokenStr)
                    .orElseThrow(() -> new CustomException("Invalid reset token"));

            if (token.getExpiry() < System.currentTimeMillis()) {
                throw new CustomException("Reset token expired");
            }

            User user = userRepository.findById(token.getUserId())
                    .orElseThrow(() -> new CustomException("User not found"));

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            tokenRepository.delete(token);
        } catch (Exception e) {
            System.err.println("RESET PASSWORD CONFIRM ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new CustomException("Password reset failed: " + e.getMessage());
        }
    }

    public void changePassword(ChangePasswordRequest request) {
        try {
            User user = getCurrentUser();

            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new CustomException("Old password is incorrect");
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                throw new CustomException("New passwords do not match");
            }

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);
        } catch (Exception e) {
            System.err.println("CHANGE PASSWORD ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new CustomException("Password change failed: " + e.getMessage());
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException("No authenticated user");
        }
        return (User) authentication.getPrincipal();
    }

    private AuthResponse buildAuthResponse(User user) {
        AuthResponse response = new AuthResponse();
        response.setToken(jwtUtil.generateToken(user));
        response.setRole(user.getRole().name());
        response.setVerified(user.isVerified());
        
        // Dashboard URLs for ALL roles (no external service call)
        String dashboardUrl = getDashboardUrlForRole(user.getRole());
        response.setDashboardUrl(dashboardUrl);
        
        return response;
    }

    private String getDashboardUrlForRole(Role role) {
        return switch (role) {
            case ADMIN -> "/admin/dashboard";
            case COLLECTOR -> "/collector/dashboard";
            case CLIENT -> "/client/dashboard";
            case OPERATIONS -> "/operations/dashboard";
            case FINANCE -> "/finance/dashboard";
            case SUPPORT -> "/support/dashboard";
            default -> "/dashboard";
        };
    }

    public String uploadProfilePicture(org.springframework.web.multipart.MultipartFile file) throws IOException {
        try {
            @SuppressWarnings("rawtypes")
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
            return (String) uploadResult.get("url");
        } catch (Exception e) {
            System.err.println("UPLOAD ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to upload profile picture: " + e.getMessage());
        }
    }
}