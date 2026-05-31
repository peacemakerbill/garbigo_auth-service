package com.garbigo.auth.repository;

import com.garbigo.auth.model.ProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ProfileViewRepository extends MongoRepository<ProfileView, String> {

    // Used for total view count
    long countByViewedUserId(String viewedUserId);

    // Used for today's / recent view count
    @Query("{ 'viewedUserId': ?0, 'viewedAt': { $gte: ?1 } }")
    long countViewsSince(String viewedUserId, Instant since);

    // FIX: Removed unbounded findByViewedUserIdOrderByViewedAtDesc — never load all records.
    // Use the bounded variants below instead.

    // Used for "who viewed me" list and stats recent viewers
    List<ProfileView> findTop50ByViewedUserIdOrderByViewedAtDesc(String viewedUserId);

    // Used for stats recent viewers preview (top 10 only)
    List<ProfileView> findTop10ByViewedUserIdOrderByViewedAtDesc(String viewedUserId);

    // Duplicate prevention: same viewer, same profile, within time window
    // Backed by compound index: ('viewedUserId', 'viewerId', 'viewedAt')
    List<ProfileView> findByViewedUserIdAndViewerIdAndViewedAtAfter(
            String viewedUserId, String viewerId, Instant after);

    // Count distinct viewers (approximation — use aggregation pipeline for exact distinct count at scale)
    @Query(value = "{ 'viewedUserId': ?0, 'viewerId': { $ne: null } }", count = true)
    long countNonAnonymousViewsByViewedUserId(String viewedUserId);
}