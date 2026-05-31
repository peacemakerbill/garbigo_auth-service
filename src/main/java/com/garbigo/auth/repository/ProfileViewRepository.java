package com.garbigo.auth.repository;

import com.garbigo.auth.model.ProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ProfileViewRepository extends MongoRepository<ProfileView, String> {

    List<ProfileView> findByViewedUserIdOrderByViewedAtDesc(String viewedUserId);

    long countByViewedUserId(String viewedUserId);

    @Query("{ 'viewedUserId': ?0, 'viewedAt': { $gte: ?1 } }")
    long countViewsSince(String viewedUserId, Instant since);

    List<ProfileView> findTop10ByViewedUserIdOrderByViewedAtDesc(String viewedUserId);

    void deleteByViewedUserId(String viewedUserId);
}