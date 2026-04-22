package com.garbigo.auth.repository;

import com.garbigo.auth.model.Like;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LikeRepository extends MongoRepository<Like, String> {
}