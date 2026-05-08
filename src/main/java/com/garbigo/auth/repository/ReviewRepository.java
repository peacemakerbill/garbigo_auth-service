package com.garbigo.auth.repository;

import com.garbigo.auth.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {
    Optional<Review> findByUserIdAndTargetIdAndTargetType(String userId, String targetId, String targetType);
    List<Review> findByTargetIdAndTargetType(String targetId, String targetType);
    long countByTargetIdAndTargetType(String targetId, String targetType);
    Double getAverageRatingByTargetIdAndTargetType(String targetId, String targetType);
}