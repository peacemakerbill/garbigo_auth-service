package com.garbigo.auth.repository;

import com.garbigo.auth.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewRepository extends MongoRepository<Review, String> {
}