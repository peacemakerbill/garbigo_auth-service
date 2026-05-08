package com.garbigo.auth.repository;

import com.garbigo.auth.model.Like;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface LikeRepository extends MongoRepository<Like, String> {
    Optional<Like> findByUserIdAndTargetIdAndTargetType(String userId, String targetId, String targetType);
    long countByTargetIdAndTargetType(String targetId, String targetType);
    void deleteByUserIdAndTargetIdAndTargetType(String userId, String targetId, String targetType);
}