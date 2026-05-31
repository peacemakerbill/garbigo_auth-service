package com.garbigo.auth.repository;

import com.garbigo.auth.model.ProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface ProfileViewRepository extends MongoRepository<ProfileView, String> {

    // Total view count for a profile
    long countByViewedUserId(String viewedUserId);

    // Views in the last 24 hours (today's views)
    long countByViewedUserIdAndViewedAtAfter(String viewedUserId, Instant since);

    // Count of distinct authenticated viewers
    long countByViewedUserIdAndViewerIdNotNull(String viewedUserId);

    // Recent 50 viewers — used for "who viewed me"
    List<ProfileView> findTop50ByViewedUserIdOrderByViewedAtDesc(String viewedUserId);

    // Recent 10 viewers — used for stats preview
    List<ProfileView> findTop10ByViewedUserIdOrderByViewedAtDesc(String viewedUserId);

    // Recent 50 profiles this user has viewed — used for "who I viewed"
    List<ProfileView> findTop50ByViewerIdOrderByViewedAtDesc(String viewerId);

    // Duplicate prevention check — backed by compound index (viewedUserId, viewerId, viewedAt)
    List<ProfileView> findByViewedUserIdAndViewerIdAndViewedAtAfter(
            String viewedUserId, String viewerId, Instant after);
}