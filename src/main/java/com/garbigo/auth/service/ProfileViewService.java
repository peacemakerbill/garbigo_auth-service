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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProfileViewService {

    private final ProfileViewRepository profileViewRepository;
    private final UserRepository userRepository;

    public ProfileViewService(ProfileViewRepository profileViewRepository, UserRepository userRepository) {
        this.profileViewRepository = profileViewRepository;
        this.userRepository = userRepository;
    }

    public void recordProfileView(String viewedUserId, String viewerId, String ip, String userAgent) {
        if (viewedUserId == null) return;

        // Prevent self-view counting
        if (viewerId != null && viewerId.equals(viewedUserId)) {
            return;
        }

        ProfileView view = new ProfileView();
        view.setViewedUserId(viewedUserId);
        view.setViewerId(viewerId);
        view.setViewerIp(ip != null ? ip.split(",")[0].trim() : null);
        view.setUserAgent(userAgent);
        view.setViewedAt(Instant.now());
        view.setAnonymous(viewerId == null);

        profileViewRepository.save(view);
    }

    public ProfileViewStatsDto getProfileViewStats(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found"));

        long totalViews = profileViewRepository.countByViewedUserId(userId);
        long todayViews = profileViewRepository.countViewsSince(userId, Instant.now().minus(1, ChronoUnit.DAYS));

        List<ProfileView> recentViews = profileViewRepository.findTop50ByViewedUserIdOrderByViewedAtDesc(userId);

        Set<String> uniqueViewerIds = recentViews.stream()
                .filter(v -> v.getViewerId() != null)
                .map(ProfileView::getViewerId)
                .collect(Collectors.toSet());

        List<ProfileViewDto> recentViewers = recentViews.stream()
                .limit(10)
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return new ProfileViewStatsDto(
                totalViews,
                uniqueViewerIds.size(),
                todayViews,
                recentViewers
        );
    }

    /**
     * Who Viewed Me - Returns full user summary data
     */
    public List<UserSummaryDto> getWhoViewedMe(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found"));

        List<ProfileView> views = profileViewRepository.findTop50ByViewedUserIdOrderByViewedAtDesc(userId);

        List<String> viewerIds = views.stream()
                .filter(v -> v.getViewerId() != null)
                .map(ProfileView::getViewerId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, User> userMap = userRepository.findAllById(viewerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return viewerIds.stream()
                .map(vid -> {
                    User viewer = userMap.get(vid);
                    if (viewer == null) return null;

                    String fullName = buildFullName(viewer);

                    return new UserSummaryDto(
                            viewer.getId(),
                            viewer.getUsername(),
                            viewer.getFirstName(),
                            viewer.getMiddleName(),
                            viewer.getLastName(),
                            fullName,
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

    private String buildFullName(User user) {
        StringBuilder sb = new StringBuilder();
        if (user.getFirstName() != null) sb.append(user.getFirstName().trim());
        if (user.getMiddleName() != null) sb.append(" ").append(user.getMiddleName().trim());
        if (user.getLastName() != null) sb.append(" ").append(user.getLastName().trim());
        return sb.toString().trim();
    }

    private ProfileViewDto convertToDto(ProfileView view) {
        if (view.isAnonymous() || view.getViewerId() == null) {
            return new ProfileViewDto(null, "Anonymous User", null, view.getViewedAt(), true);
        }

        User viewer = userRepository.findById(view.getViewerId()).orElse(null);
        String name = viewer != null ? buildFullName(viewer) : "Unknown User";

        return new ProfileViewDto(
                view.getViewerId(),
                name,
                viewer != null ? viewer.getProfilePictureUrl() : null,
                view.getViewedAt(),
                false
        );
    }
}