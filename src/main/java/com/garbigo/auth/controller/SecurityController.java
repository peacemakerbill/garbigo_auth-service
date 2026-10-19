package com.garbigo.auth.controller;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.dto.MessageResponse;
import com.garbigo.auth.dto.SecurityDtos.ActionPreviewDto;
import com.garbigo.auth.dto.SecurityDtos.ActionResultDto;
import com.garbigo.auth.dto.SecurityDtos.ActionTokenRequest;
import com.garbigo.auth.dto.SecurityDtos.CountDto;
import com.garbigo.auth.dto.SecurityDtos.DeviceDetailDto;
import com.garbigo.auth.dto.SecurityDtos.DeviceDto;
import com.garbigo.auth.dto.SecurityDtos.ReauthRequest;
import com.garbigo.auth.dto.SecurityDtos.SecurityEventDto;
import com.garbigo.auth.dto.SecurityDtos.SessionDto;
import com.garbigo.auth.dto.SecurityDtos.SignInActivityDto;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SessionRevokeReason;
import com.garbigo.auth.exception.ApiException;
import com.garbigo.auth.model.User;
import com.garbigo.auth.model.UserDevice;
import com.garbigo.auth.model.UserSession;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.ClientContextFactory;
import com.garbigo.auth.security.JwtFilter;
import com.garbigo.auth.service.DeviceService;
import com.garbigo.auth.service.ReauthService;
import com.garbigo.auth.service.SecurityActionService;
import com.garbigo.auth.service.SecurityAuditService;
import com.garbigo.auth.service.SecurityViewService;
import com.garbigo.auth.service.SessionService;
import com.garbigo.auth.util.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/security")
public class SecurityController {

    private final DeviceService deviceService;
    private final SessionService sessionService;
    private final SecurityViewService views;
    private final SecurityActionService actions;
    private final ReauthService reauth;
    private final SecurityAuditService audit;
    private final UserRepository users;
    private final ClientContextFactory contexts;
    private final SecurityProperties properties;
    private final RateLimiter rateLimiter;

    public SecurityController(DeviceService deviceService, SessionService sessionService, SecurityViewService views,
                              SecurityActionService actions, ReauthService reauth, SecurityAuditService audit,
                              UserRepository users, ClientContextFactory contexts, SecurityProperties properties,
                              RateLimiter rateLimiter) {
        this.deviceService = deviceService;
        this.sessionService = sessionService;
        this.views = views;
        this.actions = actions;
        this.reauth = reauth;
        this.audit = audit;
        this.users = users;
        this.contexts = contexts;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/devices")
    public ResponseEntity<List<DeviceDto>> devices(HttpServletRequest request) {
        User user = currentUser();
        return ResponseEntity.ok(views.devices(user.getId(), currentDeviceId(request)));
    }

    @GetMapping("/devices/{id}")
    public ResponseEntity<DeviceDetailDto> device(@PathVariable String id, HttpServletRequest request) {
        User user = currentUser();
        return ResponseEntity.ok(views.deviceDetail(user.getId(), id, currentDeviceId(request)));
    }

    @PostMapping("/devices/{id}/trust")
    public ResponseEntity<DeviceDto> trust(@PathVariable String id, @Valid @RequestBody(required = false) ReauthRequest body,
                                           HttpServletRequest request) {
        User user = currentUser();
        UserSession session = currentSession(request);
        reauth.require(user, session, body == null ? null : body.getPassword(), contexts.from(request));
        UserDevice device = deviceService.trust(user.getId(), id);
        return ResponseEntity.ok(views.deviceSummary(user.getId(), device, currentDeviceId(request)));
    }

    @DeleteMapping("/devices/{id}/trust")
    public ResponseEntity<DeviceDto> revokeTrust(@PathVariable String id, HttpServletRequest request) {
        User user = currentUser();
        UserDevice device = deviceService.revokeTrust(user.getId(), id);
        return ResponseEntity.ok(views.deviceSummary(user.getId(), device, currentDeviceId(request)));
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<CountDto> removeDevice(@PathVariable String id) {
        requireRevocationEnabled();
        User user = currentUser();
        int ended = deviceService.remove(user.getId(), id);
        return ResponseEntity.ok(new CountDto("The device was removed and its sessions were signed out.", ended));
    }

    @PostMapping("/devices/revoke-remembered")
    public ResponseEntity<CountDto> revokeRemembered() {
        User user = currentUser();
        int count = deviceService.revokeAllRemembered(user.getId());
        return ResponseEntity.ok(new CountDto("This service no longer remembers any of your devices.", count));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionDto>> sessions(HttpServletRequest request) {
        User user = currentUser();
        return ResponseEntity.ok(views.sessions(user.getId(), currentJti(request)));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<MessageResponse> endSession(@PathVariable String id) {
        requireRevocationEnabled();
        User user = currentUser();
        sessionService.revoke(user.getId(), id, SessionRevokeReason.REVOKED_BY_USER);
        return ResponseEntity.ok(new MessageResponse("That session was signed out."));
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<CountDto> endOtherSessions() {
        requireRevocationEnabled();
        User user = currentUser();
        String token = currentRawToken();
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again to continue.");
        }
        int count = sessionService.revokeOthers(user, token, SessionRevokeReason.REVOKED_OTHERS_BY_USER);
        return ResponseEntity.ok(new CountDto("Your other sessions were signed out.", count));
    }

    @GetMapping("/activity")
    public ResponseEntity<List<SignInActivityDto>> activity(@RequestParam(defaultValue = "20") int limit) {
        User user = currentUser();
        return ResponseEntity.ok(views.activity(user.getId(), limit));
    }

    @PostMapping("/actions/inspect")
    public ResponseEntity<ActionPreviewDto> inspect(@Valid @RequestBody ActionTokenRequest request) {
        rateLimiter.checkRateLimit();
        return ResponseEntity.ok(actions.inspect(request.getToken()));
    }

    @PostMapping("/actions/report-unauthorized")
    public ResponseEntity<ActionResultDto> reportUnauthorized(@Valid @RequestBody ActionTokenRequest request) {
        rateLimiter.checkRateLimit();
        boolean reset = !Boolean.FALSE.equals(request.getRequestPasswordReset());
        return ResponseEntity.ok(actions.reportUnauthorized(request.getToken(), reset));
    }

    @GetMapping("/admin/events")
    public ResponseEntity<Page<SecurityEventDto>> events(@RequestParam(required = false) String userId,
                                                         @RequestParam(required = false) SecurityEventType type,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(views.events(userId, type, page, size));
    }

    @PostMapping("/admin/users/{userId}/revoke-sessions")
    public ResponseEntity<CountDto> revokeUserSessions(@PathVariable String userId) {
        User admin = currentUser();
        User target = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "We couldn't find that user."));
        int count = sessionService.revokeAll(target.getId(), SessionRevokeReason.REVOKED_BY_ADMIN);
        audit.record(SecurityEventType.SESSIONS_REVOKED, target.getId(), admin.getId(),
                "reason=REVOKED_BY_ADMIN, count=" + count);
        return ResponseEntity.ok(new CountDto("All sessions for this user were signed out.", count));
    }

    private void requireRevocationEnabled() {
        if (!properties.isSessionRevocationEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Signing out sessions from here is turned off.");
        }
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        return user;
    }

    private String currentRawToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token && !token.isBlank()) {
            return token;
        }
        return null;
    }

    private String currentJti(HttpServletRequest request) {
        Object jti = request.getAttribute(JwtFilter.SESSION_JTI_ATTRIBUTE);
        return jti instanceof String value ? value : null;
    }

    private UserSession currentSession(HttpServletRequest request) {
        return sessionService.findByJti(currentJti(request)).orElse(null);
    }

    private String currentDeviceId(HttpServletRequest request) {
        UserSession session = currentSession(request);
        return session == null ? null : session.getDeviceId();
    }
}
