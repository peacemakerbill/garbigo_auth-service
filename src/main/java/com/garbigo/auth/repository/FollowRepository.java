package com.garbigo.auth.repository;

import com.garbigo.auth.model.Follow;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface FollowRepository extends MongoRepository<Follow, String> {
    Optional<Follow> findByUserIdAndFollowerId(String userId, String followerId);
    List<Follow> findByUserId(String userId);           // Followers of a user
    List<Follow> findByFollowerId(String followerId);  // Following list
    long countByUserId(String userId);
    long countByFollowerId(String followerId);
    void deleteByUserIdAndFollowerId(String userId, String followerId);
}