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
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
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

	public AuthService(UserRepository userRepository, TokenRepository tokenRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtUtil jwtUtil, EmailService emailService,
			Cloudinary cloudinary, RabbitTemplate rabbitTemplate, RateLimiter rateLimiter) {
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
	public MessageResponse signup(SignupRequest request) {
		try {
			rateLimiter.checkRateLimit();

			if (userRepository.findByEmail(request.getEmail()).isPresent()) {
				throw new CustomException("This email is already registered. Please sign in or use a different email.");
			}

			if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
				if (userRepository.findByDisplayUsername(request.getUsername()).isPresent()) {
					throw new CustomException("This username is already taken. Please choose a different one.");
				}
			}

			if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
				if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
					throw new CustomException("This phone number is already registered.");
				}
			}

			User user = new User();
			user.setDisplayUsername(request.getUsername());
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
			long expiryMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
			token.setExpiry(expiryMillis);
			tokenRepository.save(token);

			sendEmailAndRabbitMQAsync(user, verifyToken, Instant.ofEpochMilli(expiryMillis));

			return new MessageResponse("Your account has been created! Please check your email to verify your account.");

		} catch (CustomException e) {
			System.err.println("SIGNUP ERROR: " + e.getMessage());
			throw e;
		} catch (Exception e) {
			System.err.println("SIGNUP ERROR: " + e.getMessage());
			e.printStackTrace();
			throw new CustomException("We couldn't complete your sign up. Please try again.");
		}
	}

	public void resendVerificationEmail(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new CustomException("We couldn't find an account with that email."));

		if (user.isVerified()) {
			throw new CustomException("This account is already verified.");
		}

		String verifyToken = UUID.randomUUID().toString();
		Token token = new Token();
		token.setUserId(user.getId());
		token.setToken(verifyToken);
		token.setType("VERIFICATION");
		long expiryMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
		token.setExpiry(expiryMillis);
		tokenRepository.save(token);

		Instant expiresAt = Instant.ofEpochMilli(expiryMillis);
		CompletableFuture.runAsync(() -> {
			try {
				emailService.sendVerificationEmail(user.getEmail(), displayName(user), verifyToken, expiresAt);
				System.out.println("Resent verification email to: " + user.getEmail());
			} catch (MessagingException e) {
				System.err.println("Failed to resend verification email: " + e.getMessage());
			}
		});
	}

	private String displayName(User user) {
		return (user.getFirstName() != null && !user.getFirstName().isBlank()) ? user.getFirstName() : "there";
	}

	private void sendEmailAndRabbitMQAsync(User user, String verifyToken, Instant expiresAt) {
		CompletableFuture.runAsync(() -> {
			try {
				emailService.sendVerificationEmail(user.getEmail(), displayName(user), verifyToken, expiresAt);
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

			Authentication authentication = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

			User user = (User) authentication.getPrincipal();
			return buildAuthResponse(user);

		} catch (DisabledException e) {
			System.err.println("SIGNIN BLOCKED for " + request.getEmail() + ": account disabled");
			User user = userRepository.findByEmail(request.getEmail()).orElse(null);
			if (user != null && !user.isVerified()) {
				throw new CustomException("Please verify your email before signing in. Check your inbox for "
						+ "the verification link, or request a new one if it has expired.");
			}
			if (user != null && user.isArchived()) {
				throw new CustomException("This account has been closed. Please contact support for help.");
			}
			throw new CustomException("This account is currently inactive. Please contact support for help.");
		} catch (AuthenticationException e) {
			System.err.println("SIGNIN ERROR for " + request.getEmail() + ": " + e.getClass().getSimpleName());
			throw new CustomException("Invalid email or password");
		} catch (Exception e) {
			System.err.println("SIGNIN ERROR: " + e.getMessage());
			e.printStackTrace();
			throw new CustomException("We couldn't sign you in right now. Please try again.");
		}
	}

	public void verifyAccount(String tokenStr) {
		try {
			Token token = tokenRepository.findByToken(tokenStr)
					.orElseThrow(() -> new CustomException("This verification link isn't valid."));

			if (token.getExpiry() < System.currentTimeMillis()) {
				throw new CustomException("This verification link has expired. Please request a new one.");
			}

			User user = userRepository.findById(token.getUserId())
					.orElseThrow(() -> new CustomException("We couldn't find an account for this link."));

			user.setVerified(true);
			userRepository.save(user);
			tokenRepository.delete(token);
		} catch (CustomException e) {
			System.err.println("VERIFY ERROR: " + e.getMessage());
			throw e;
		} catch (Exception e) {
			System.err.println("VERIFY ERROR: " + e.getMessage());
			e.printStackTrace();
			throw new CustomException("We couldn't verify your account right now. Please try again.");
		}
	}

	public void requestPasswordReset(String email) {
		try {
			rateLimiter.checkRateLimit();

			User user = userRepository.findByEmail(email)
					.orElseThrow(() -> new CustomException("We couldn't find an account with that email."));

			String resetToken = UUID.randomUUID().toString();
			Token token = new Token();
			token.setUserId(user.getId());
			token.setToken(resetToken);
			token.setType("RESET");
			long expiryMillis = System.currentTimeMillis() + 60 * 60 * 1000;
			token.setExpiry(expiryMillis);
			tokenRepository.save(token);

			Instant expiresAt = Instant.ofEpochMilli(expiryMillis);
			CompletableFuture.runAsync(() -> {
				try {
					emailService.sendResetPasswordEmail(email, displayName(user), resetToken, expiresAt);
				} catch (MessagingException e) {
					System.err.println("Failed to send reset email: " + e.getMessage());
				}
			});
		} catch (CustomException e) {
			System.err.println("RESET PASSWORD ERROR: " + e.getMessage());
			throw e;
		} catch (Exception e) {
			System.err.println("RESET PASSWORD ERROR: " + e.getMessage());
			e.printStackTrace();
			throw new CustomException("We couldn't send the password reset email. Please try again.");
		}
	}

	public void resetPassword(String tokenStr, String newPassword) {
		try {
			Token token = tokenRepository.findByToken(tokenStr)
					.orElseThrow(() -> new CustomException("This password reset link isn't valid."));

			if (token.getExpiry() < System.currentTimeMillis()) {
				throw new CustomException("This password reset link has expired. Please request a new one.");
			}

			User user = userRepository.findById(token.getUserId())
					.orElseThrow(() -> new CustomException("We couldn't find an account for this link."));

			user.setPassword(passwordEncoder.encode(newPassword));
			userRepository.save(user);
			tokenRepository.delete(token);
		} catch (CustomException e) {
			System.err.println("RESET PASSWORD CONFIRM ERROR: " + e.getMessage());
			throw e;
		} catch (Exception e) {
			System.err.println("RESET PASSWORD CONFIRM ERROR: " + e.getMessage());
			e.printStackTrace();
			throw new CustomException("We couldn't reset your password. Please try again.");
		}
	}

	public void changePassword(ChangePasswordRequest request) {
		try {
			User user = getCurrentUser();

			if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
				throw new CustomException("Your current password is incorrect.");
			}

			if (!request.getNewPassword().equals(request.getConfirmPassword())) {
				throw new CustomException("New passwords do not match.");
			}

			user.setPassword(passwordEncoder.encode(request.getNewPassword()));
			userRepository.save(user);
		} catch (CustomException e) {
			System.err.println("CHANGE PASSWORD ERROR: " + e.getMessage());
			throw e;
		} catch (Exception e) {
			System.err.println("CHANGE PASSWORD ERROR: " + e.getMessage());
			e.printStackTrace();
			throw new CustomException("We couldn't change your password. Please try again.");
		}
	}

	private User getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new CustomException("Please sign in to continue.");
		}
		return (User) authentication.getPrincipal();
	}

	private AuthResponse buildAuthResponse(User user) {
		JwtUtil.GeneratedToken generated = jwtUtil.generateToken(user);
		return new AuthResponse(generated.token(), user.getRole().name(), generated.expiresAt());
	}

	public String uploadProfilePicture(org.springframework.web.multipart.MultipartFile file) throws IOException {
		try {
			@SuppressWarnings("rawtypes")
			Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
			return (String) uploadResult.get("url");
		} catch (Exception e) {
			System.err.println("UPLOAD ERROR: " + e.getMessage());
			e.printStackTrace();
			throw new IOException("We couldn't upload your profile picture. Please try again.");
		}
	}
}