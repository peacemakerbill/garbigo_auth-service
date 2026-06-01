package com.garbigo.auth.controller;

import com.garbigo.auth.dto.ProfileViewStatsDto;
import com.garbigo.auth.dto.UserSummaryDto;
import com.garbigo.auth.model.User;
import com.garbigo.auth.service.ProfileViewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profile-views")
public class ProfileViewController {

    private final ProfileViewService profileViewService;

    public ProfileViewController(ProfileViewService profileViewService) {
        this.profileViewService = profileViewService;
    }

    /**
     * Record a profile view.
     * Authenticated: viewer ID extracted from security context.
     * Unauthenticated: recorded as anonymous.
     */
    @PostMapping("/{viewedUserId}")
    public ResponseEntity<String> recordView(
            @PathVariable String viewedUserId,
            @AuthenticationPrincipal User currentUser,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        String trimmedId = viewedUserId != null ? viewedUserId.trim() : "";
        if (trimmedId.isEmpty() || "who-viewed-me".equals(trimmedId) || "who-i-viewed".equals(trimmedId)) {
            return ResponseEntity.badRequest().body("Invalid profile ID");
        }

        String viewerId = currentUser != null ? currentUser.getId() : null;
        profileViewService.recordProfileView(trimmedId, viewerId, forwardedFor, userAgent);

        return ResponseEntity.ok("Profile view recorded successfully");
    }

    /**
     * Get profile view statistics for a given user.
     */
    @GetMapping("/stats/{userId}")
    public ResponseEntity<ProfileViewStatsDto> getStats(@PathVariable String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(profileViewService.getProfileViewStats(userId));
    }

    /**
     * Get profile view statistics for the authenticated user.
     */
    @GetMapping("/my-stats")
    public ResponseEntity<ProfileViewStatsDto> getMyStats(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(profileViewService.getProfileViewStats(currentUser.getId()));
    }

    /**
     * Who Viewed Me — list of users who visited the authenticated user's profile.
     * Requires authentication.
     */
    @GetMapping("/who-viewed-me")
    public ResponseEntity<List<UserSummaryDto>> getWhoViewedMe(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(profileViewService.getWhoViewedMe(currentUser.getId()));
    }

    /**
     * Who I Viewed — list of profiles the authenticated user has visited.
     * Requires authentication.
     */
    @GetMapping("/who-i-viewed")
    public ResponseEntity<List<UserSummaryDto>> getWhoIViewed(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(profileViewService.getWhoIViewed(currentUser.getId()));
    }
}