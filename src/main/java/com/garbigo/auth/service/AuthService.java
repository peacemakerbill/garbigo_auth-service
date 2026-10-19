package com.garbigo.auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.dto.AuthRequest;
import com.garbigo.auth.dto.AuthResponse;
import com.garbigo.auth.dto.ChangePasswordRequest;
import com.garbigo.auth.dto.MessageResponse;
import com.garbigo.auth.dto.SignInResult;
import com.garbigo.auth.dto.SignupRequest;
import com.garbigo.auth.enums.Role;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SessionRevokeReason;
import com.garbigo.auth.enums.SignInMethod;
import com.garbigo.auth.exception.ApiException;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.Token;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.TokenRepository;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.ClientContext;
import com.garbigo.auth.security.ClientContextFactory;
import com.garbigo.auth.security.JwtUtil;
import com.garbigo.auth.security.LoginThrottle;
import com.garbigo.auth.security.TokenBlacklistService;
import com.garbigo.auth.util.EmailAddresses;
import com.garbigo.auth.util.RateLimiter;
import com.garbigo.auth.util.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private static final String SIGNUP_MESSAGE =
			"Your account has been created! Please check your email to verify your account.";

	private final UserRepository userRepository;
	private final TokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtUtil jwtUtil;
	private final Cloudinary cloudinary;
	private final RabbitTemplate rabbitTemplate;
	private final RateLimiter rateLimiter;
	private final AccountRecoveryService accountRecovery;
	private final AdminRoleService adminRoleService;
	private final SessionService sessionService;
	private final SignInSecurityService signInSecurity;
	private final SecurityAuditService audit;
	private final SecurityNotificationService notifications;
	private final LoginThrottle throttle;
	private final TokenBlacklistService tokenBlacklist;
	private final ClientContextFactory contexts;
	private final SecurityProperties properties;

	@Value("${rabbitmq.queue.user-created}")
	private String userCreatedQueue;

	public AuthService(UserRepository userRepository, TokenRepository tokenRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtUtil jwtUtil, Cloudinary cloudinary,
			RabbitTemplate rabbitTemplate, RateLimiter rateLimiter, AccountRecoveryService accountRecovery,
			AdminRoleService adminRoleService, SessionService sessionService, SignInSecurityService signInSecurity,
			SecurityAuditService audit, SecurityNotificationService notifications, LoginThrottle throttle,
			TokenBlacklistService tokenBlacklist, ClientContextFactory contexts, SecurityProperties properties) {
		this.userRepository = userRepository;
		this.tokenRepository = tokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
		this.cloudinary = cloudinary;
		this.rabbitTemplate = rabbitTemplate;
		this.rateLimiter = rateLimiter;
		this.accountRecovery = accountRecovery;
		this.adminRoleService = adminRoleService;
		this.sessionService = sessionService;
		this.signInSecurity = signInSecurity;
		this.audit = audit;
		this.notifications = notifications;
		this.throttle = throttle;
		this.tokenBlacklist = tokenBlacklist;
		this.contexts = contexts;
		this.properties = properties;
	}

	public MessageResponse signup(SignupRequest request) {
		try {
			rateLimiter.checkRateLimit();

			String email = EmailAddresses.normalize(request.getEmail());
			if (email == null) {
				throw new CustomException("Please enter a valid email address.");
			}

			if (!userRepository.findAllByEmailIgnoreCase(email).isEmpty()) {
				if (properties.isHideAccountExistenceOnSignup()) {
					notifications.notifyAccountAlreadyExists(email);
					return new MessageResponse(SIGNUP_MESSAGE);
				}
				throw new CustomException("This email is already registered. Please sign in or use a different email.");
			}

			if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
				if (!userRepository.findByDisplayUsername(request.getUsername()).isEmpty()) {
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
			user.setEmail(email);
			user.setPhoneNumber(request.getPhoneNumber());
			user.setHomeAddress(request.getHomeAddress());
			user.setPassword(passwordEncoder.encode(request.getPassword()));
			user.setRole(Role.CLIENT);

			user = userRepository.save(user);

			accountRecovery.sendVerificationEmail(user);
			publishUserCreated(user);

			return new MessageResponse(SIGNUP_MESSAGE);

		} catch (CustomException e) {
			log.warn("Sign up was not completed: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Sign up failed", e);
			throw new CustomException("We couldn't complete your sign up. Please try again.");
		}
	}

	public void resendVerificationEmail(String email) {
		rateLimiter.checkRateLimit();

		User user = findAccount(email);
		if (user != null && !user.isVerified() && user.isActive() && !user.isArchived()) {
			accountRecovery.sendVerificationEmail(user);
		}
	}

	private void publishUserCreated(User user) {
		User payload = new User();
		BeanUtils.copyProperties(user, payload);
		payload.setPassword(null);

		CompletableFuture.runAsync(() -> {
			try {
				rabbitTemplate.convertAndSend(userCreatedQueue, payload);
			} catch (Exception e) {
				log.warn("Could not publish the new user event: {}", e.getClass().getSimpleName());
			}
		});
	}

	public SignInResult signin(AuthRequest request, ClientContext ctx) {
		try {
			rateLimiter.checkRateLimit();

			String key = SignInSecurityService.emailKey(request.getEmail());
			LoginThrottle.Status status = throttle.check(key, ctx.ip());
			if (status.locked()) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
						"Too many sign-in attempts. Please wait a few minutes and try again.",
						Math.max(1L, status.retryAfter().toSeconds()));
			}

			User existing = findAccount(request.getEmail());
			String loginEmail = existing != null ? existing.getEmail() : request.getEmail();

			try {
				Authentication authentication = authenticationManager
						.authenticate(new UsernamePasswordAuthenticationToken(loginEmail, request.getPassword()));
				User user = (User) authentication.getPrincipal();
				return finishSignIn(user, SignInMethod.PASSWORD, ctx);

			} catch (DisabledException | LockedException e) {
				throw blockedAccount(existing, request.getPassword(), key, ctx);
			} catch (AuthenticationException e) {
				registerFailure(existing, key, ctx, "WRONG_CREDENTIALS");
				throw new CustomException("Invalid email or password");
			}

		} catch (CustomException e) {
			throw e;
		} catch (Exception e) {
			log.error("Sign in failed", e);
			throw new CustomException("We couldn't sign you in right now. Please try again.");
		}
	}

	public SignInResult finishSignIn(User user, SignInMethod method, ClientContext ctx) {
		JwtUtil.GeneratedToken generated = jwtUtil.generateToken(user);
		SignInSecurityService.Result result = signInSecurity.completeSignIn(user, generated, method, ctx);
		AuthResponse response = new AuthResponse(generated.token(), user.getRole().name(), generated.expiresAt());
		return new SignInResult(response, result.newDeviceToken());
	}

	private CustomException blockedAccount(User existing, String password, String key, ClientContext ctx) {
		boolean passwordMatches = existing != null && existing.getPassword() != null && password != null
				&& passwordEncoder.matches(password, existing.getPassword());

		if (!passwordMatches) {
			registerFailure(existing, key, ctx, "WRONG_CREDENTIALS");
			return new CustomException("Invalid email or password");
		}

		if (!existing.isVerified()) {
			return new CustomException("Please verify your email before signing in. Check your inbox for "
					+ "the verification link, or request a new one if it has expired.");
		}
		if (existing.isArchived()) {
			return new CustomException("This account has been closed. Please contact support for help.");
		}
		return new CustomException("This account is currently inactive. Please contact support for help.");
	}

	private void registerFailure(User existing, String key, ClientContext ctx, String reason) {
		LoginThrottle.FailureResult result = throttle.recordFailure(key, ctx.ip());

		if (existing != null) {
			try {
				signInSecurity.recordFailure(existing, SignInMethod.PASSWORD, reason, ctx);
			} catch (Exception e) {
				log.debug("Could not store a failed sign-in: {}", e.getClass().getSimpleName());
			}

			if (result.justLocked()) {
				long minutes = Math.max(1L, result.lockedFor().toMinutes());
				audit.recordWithIp(SecurityEventType.ACCOUNT_LOCKED, existing.getId(), null, ctx.ip(),
						"scope=" + result.scope().name() + ", minutes=" + minutes);
				notifications.notifyAccountLocked(existing.getId(), minutes, ctx.ip());
			}
		}
	}

	public String verifyAccount(String tokenStr) {
		try {
			Token token = tokenRepository.findByToken(tokenStr)
					.filter(t -> "VERIFICATION".equals(t.getType()))
					.orElseThrow(() -> new CustomException("This verification link isn't valid."));

			if (token.getExpiry() < System.currentTimeMillis()) {
				throw new CustomException("This verification link has expired. Please request a new one.");
			}

			User user = userRepository.findById(token.getUserId())
					.orElseThrow(() -> new CustomException("We couldn't find an account for this link."));

			boolean firstTime = !user.isVerified();
			boolean forcePasswordSetup = firstTime && user.getPassword() != null
					&& user.getRole() != Role.ADMIN
					&& adminRoleService.isEligibleEmail(user.getEmail())
					&& properties.isAdminVerificationRequiresNewPassword();

			user.setVerified(true);
			if (forcePasswordSetup) {
				user.setPassword(passwordEncoder.encode(SecureTokens.generate()));
			}
			userRepository.save(user);
			tokenRepository.delete(token);

			if (firstTime) {
				audit.record(SecurityEventType.EMAIL_VERIFIED, user.getId(), user.getId(), null);
			}

			adminRoleService.grantIfEligible(user, AdminRoleService.Source.EMAIL_VERIFICATION, true);

			if (forcePasswordSetup) {
				accountRecovery.sendPasswordResetEmail(user);
				return "Your email is verified. Because this address has administrator access, we sent you a "
						+ "link to choose your password. Please use it to finish setting up your account.";
			}
			return "Account verified successfully";
		} catch (CustomException e) {
			log.warn("Verification was not completed: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Verification failed", e);
			throw new CustomException("We couldn't verify your account right now. Please try again.");
		}
	}

	public void requestPasswordReset(String email) {
		rateLimiter.checkRateLimit();

		User user = findAccount(email);
		if (user != null && user.isActive() && !user.isArchived()) {
			accountRecovery.sendPasswordResetEmail(user);
		}
	}

	public void resetPassword(String tokenStr, String newPassword) {
		try {
			Token token = tokenRepository.findByToken(tokenStr)
					.filter(t -> "RESET".equals(t.getType()))
					.orElseThrow(() -> new CustomException("This password reset link isn't valid."));

			if (token.getExpiry() < System.currentTimeMillis()) {
				throw new CustomException("This password reset link has expired. Please request a new one.");
			}

			User user = userRepository.findById(token.getUserId())
					.orElseThrow(() -> new CustomException("We couldn't find an account for this link."));

			tokenRepository.delete(token);
			tokenRepository.deleteByUserIdAndType(user.getId(), "RESET");

			user.setPassword(passwordEncoder.encode(newPassword));
			userRepository.save(user);

			sessionService.revokeAll(user.getId(), SessionRevokeReason.PASSWORD_RESET);

			String ip = contexts.current().ip();
			audit.recordWithIp(SecurityEventType.PASSWORD_RESET_COMPLETED, user.getId(), user.getId(), ip, null);
			notifications.notifyPasswordResetCompleted(user.getId(), ip);
		} catch (CustomException e) {
			log.warn("Password reset was not completed: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Password reset failed", e);
			throw new CustomException("We couldn't reset your password. Please try again.");
		}
	}

	public void changePassword(ChangePasswordRequest request) {
		try {
			User user = getCurrentUser();

			if (user.getPassword() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
				throw new CustomException("Your current password is incorrect.");
			}

			if (!request.getNewPassword().equals(request.getConfirmPassword())) {
				throw new CustomException("New passwords do not match.");
			}

			user.setPassword(passwordEncoder.encode(request.getNewPassword()));
			userRepository.save(user);

			String currentToken = currentRawToken();
			if (currentToken != null) {
				sessionService.revokeOthers(user, currentToken, SessionRevokeReason.PASSWORD_CHANGED);
			} else {
				sessionService.revokeAll(user.getId(), SessionRevokeReason.PASSWORD_CHANGED);
			}

			String ip = contexts.current().ip();
			audit.recordWithIp(SecurityEventType.PASSWORD_CHANGED, user.getId(), user.getId(), ip, null);
			notifications.notifyPasswordChanged(user.getId(), ip);
		} catch (CustomException e) {
			log.warn("Password change was not completed: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Password change failed", e);
			throw new CustomException("We couldn't change your password. Please try again.");
		}
	}

	public void afterEmailChange(User user, String previousEmail) {
		sessionService.revokeAll(user.getId(), SessionRevokeReason.EMAIL_CHANGED);
		audit.record(SecurityEventType.EMAIL_CHANGED, user.getId(), user.getId(), null);
		accountRecovery.sendVerificationEmail(user);
		notifications.notifyEmailChanged(user.getId(), previousEmail, user.getEmail());
	}

	public void logout(String rawToken) {
		if (rawToken == null) {
			return;
		}
		try {
			sessionService.revokeByJti(jwtUtil.extractJti(rawToken), SessionRevokeReason.LOGOUT);
			tokenBlacklist.revoke(rawToken);
		} catch (Exception e) {
			log.debug("Logout was called with a token that could not be read: {}", e.getClass().getSimpleName());
		}
	}

	private User findAccount(String email) {
		String normalized = EmailAddresses.normalize(email);
		if (normalized == null) {
			return null;
		}
		User exact = userRepository.findByEmail(normalized).orElse(null);
		if (exact != null) {
			return exact;
		}
		List<User> matches = userRepository.findAllByEmailIgnoreCase(normalized);
		return matches.size() == 1 ? matches.get(0) : null;
	}

	private User getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
			throw new CustomException("Please sign in to continue.");
		}
		return (User) authentication.getPrincipal();
	}

	private String currentRawToken() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getCredentials() instanceof String token && !token.isBlank()) {
			return token;
		}
		return null;
	}

	public String uploadProfilePicture(org.springframework.web.multipart.MultipartFile file) throws IOException {
		try {
			@SuppressWarnings("rawtypes")
			Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
			return (String) uploadResult.get("url");
		} catch (Exception e) {
			log.error("Profile picture upload failed", e);
			throw new IOException("We couldn't upload your profile picture. Please try again.");
		}
	}
}
