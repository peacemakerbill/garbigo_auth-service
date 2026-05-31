package com.garbigo.auth.service;

import com.garbigo.auth.dto.ProfileViewDto;
import com.garbigo.auth.dto.ProfileViewStatsDto;
import com.garbigo.auth.dto.UserSummaryDto;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.ProfileView;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.ProfileViewRepository;
import com.garbigo.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProfileViewService {

    private final ProfileViewRepository profileViewRepository;
    private final UserRepository userRepository;

    public ProfileViewService(ProfileViewRepository profileViewRepository, UserRepository userRepository) {
        this.profileViewRepository = profileViewRepository;
        this.userRepository = userRepository;
    }

    /**
     * Record a profile view with duplicate prevention.
     * - Ignores self-views
     * - Ignores repeated views from the same authenticated user within 60 minutes
     * - Allows anonymous views (viewerId == null) through without duplicate checks
     */
    public void recordProfileView(String viewedUserId, String viewerId, String ip, String userAgent) {
        if (viewedUserId == null || viewedUserId.trim().isEmpty()) {
            return;
        }

        // Prevent self-view
        if (viewerId != null && viewerId.equals(viewedUserId)) {
            return;
        }

        // Prevent duplicate authenticated views within the last 60 minutes
        if (viewerId != null && hasRecentView(viewedUserId, viewerId)) {
            return;
        }

        ProfileView view = new ProfileView();
        view.setViewedUserId(viewedUserId);
        view.setViewerId(viewerId);
        // Take only the first IP in case of proxy chain (e.g. "1.2.3.4, 5.6.7.8")
        view.setViewerIp(ip != null ? ip.split(",")[0].trim() : null);
        view.setUserAgent(userAgent);
        // Setting it manually overrides auditing and can cause inconsistencies.
        view.setAnonymous(viewerId == null);

        profileViewRepository.save(view);
    }

    /**
     * Returns true if the same viewer already has a recorded view within the last 60 minutes.
     */
    private boolean hasRecentView(String viewedUserId, String viewerId) {
        Instant oneHourAgo = Instant.now().minus(60, ChronoUnit.MINUTES);
        List<ProfileView> recentViews = profileViewRepository
                .findByViewedUserIdAndViewerIdAndViewedAtAfter(viewedUserId, viewerId, oneHourAgo);
        return !recentViews.isEmpty();
    }

    /**
     * Returns view statistics for a given user's profile.
     */
    public ProfileViewStatsDto getProfileViewStats(String userId) {
        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new CustomException("User not found");
        }

        long totalViews = profileViewRepository.countByViewedUserId(userId);
        long todayViews = profileViewRepository.countViewsSince(userId, Instant.now().minus(1, ChronoUnit.DAYS));

        // uniqueViewers counted from DB — not approximated from a top-50 slice
        long uniqueViewers = profileViewRepository.countNonAnonymousViewsByViewedUserId(userId);

        // Load top 10 recent views for the preview list
        List<ProfileView> recentViews = profileViewRepository.findTop10ByViewedUserIdOrderByViewedAtDesc(userId);

        //Batch-fetch all viewer users in one query to avoid N+1 DB calls
        List<String> viewerIds = recentViews.stream()
                .filter(v -> !v.isAnonymous() && v.getViewerId() != null)
                .map(ProfileView::getViewerId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, User> userMap = viewerIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(viewerIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<ProfileViewDto> recentViewDtos = recentViews.stream()
                .map(v -> convertToDto(v, userMap))
                .collect(Collectors.toList());

        return new ProfileViewStatsDto(totalViews, uniqueViewers, todayViews, recentViewDtos);
    }

    /**
     * Returns full user summaries for everyone who viewed the given user's profile.
     */
    public List<UserSummaryDto> getWhoViewedMe(String userId) {
        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new CustomException("User not found");
        }

        List<ProfileView> views = profileViewRepository.findTop50ByViewedUserIdOrderByViewedAtDesc(userId);

        // Collect distinct authenticated viewer IDs, excluding self
        List<String> viewerIds = views.stream()
                .filter(v -> v.getViewerId() != null && !v.getViewerId().equals(userId))
                .map(ProfileView::getViewerId)
                .distinct()
                .collect(Collectors.toList());

        if (viewerIds.isEmpty()) {
            return List.of();
        }

        Map<String, User> userMap = userRepository.findAllById(viewerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return viewerIds.stream()
                .map(vid -> {
                    User viewer = userMap.get(vid);
                    if (viewer == null) return null; // viewer account deleted

                    return new UserSummaryDto(
                            viewer.getId(),
                            viewer.getUsername(),
                            viewer.getFirstName(),
                            viewer.getMiddleName(),
                            viewer.getLastName(),
                            buildFullName(viewer),
                            viewer.getProfilePictureUrl(),
                            viewer.getEmail(),
                            viewer.getPhoneNumber(),
                            viewer.getRole() != null ? viewer.getRole().name() : "CLIENT",
                            viewer.isActive(),
                            null
                    );
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    /**
     * when first name is absent, and each part is only appended if non-null and non-blank.
     */
    private String buildFullName(User user) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, user.getFirstName());
        appendIfPresent(sb, user.getMiddleName());
        appendIfPresent(sb, user.getLastName());
        return sb.toString().trim();
    }

    private void appendIfPresent(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(part.trim());
        }
    }

    /**
     * Convert a ProfileView to a DTO using a pre-fetched user map (no extra DB calls).
     */
    private ProfileViewDto convertToDto(ProfileView view, Map<String, User> userMap) {
        if (view.isAnonymous() || view.getViewerId() == null) {
            return new ProfileViewDto(null, "Anonymous User", null, view.getViewedAt(), true);
        }

        User viewer = userMap.get(view.getViewerId());
        String name = viewer != null ? buildFullName(viewer) : "Unknown User";
        String pictureUrl = viewer != null ? viewer.getProfilePictureUrl() : null;

        return new ProfileViewDto(view.getViewerId(), name, pictureUrl, view.getViewedAt(), false);
    }
}