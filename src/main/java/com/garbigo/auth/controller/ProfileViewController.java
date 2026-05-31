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
     * Record a profile view
     */
    @PostMapping("/{viewedUserId}")
    public ResponseEntity<String> recordView(
            @PathVariable String viewedUserId,
            @AuthenticationPrincipal User currentUser,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        if (viewedUserId == null || viewedUserId.trim().isEmpty() || "who-viewed-me".equals(viewedUserId)) {
            return ResponseEntity.badRequest().body("Invalid viewed user ID");
        }

        String viewerId = currentUser != null ? currentUser.getId() : null;
        profileViewService.recordProfileView(viewedUserId, viewerId, forwardedFor, userAgent);

        return ResponseEntity.ok("Profile view recorded successfully");
    }

    /**
     * Get profile view statistics
     */
    @GetMapping("/stats/{userId}")
    public ResponseEntity<ProfileViewStatsDto> getStats(@PathVariable String userId) {
        return ResponseEntity.ok(profileViewService.getProfileViewStats(userId));
    }

    /**
     * Who Viewed Me - Full list of users who viewed your profile
     */
    @GetMapping("/who-viewed-me")
    public ResponseEntity<List<UserSummaryDto>> getWhoViewedMe(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(profileViewService.getWhoViewedMe(currentUser.getId()));
    }
}