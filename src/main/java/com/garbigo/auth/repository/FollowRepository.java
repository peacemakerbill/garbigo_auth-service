package com.garbigo.auth.repository;

import com.garbigo.auth.model.Follow;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FollowRepository extends MongoRepository<Follow, String> {
}