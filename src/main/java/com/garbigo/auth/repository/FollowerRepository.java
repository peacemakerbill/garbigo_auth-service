package com.garbigo.auth.repository;

import com.garbigo.auth.model.Follower;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FollowerRepository extends MongoRepository<Follower, String> {
}