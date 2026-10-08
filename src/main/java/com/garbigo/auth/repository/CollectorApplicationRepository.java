package com.garbigo.auth.repository;

import com.garbigo.auth.model.ApplicationStatus;
import com.garbigo.auth.model.CollectorApplication;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CollectorApplicationRepository extends MongoRepository<CollectorApplication, String> {

    Optional<CollectorApplication> findFirstByUserIdOrderByCreatedAtDesc(String userId);

    List<CollectorApplication> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByStatus(ApplicationStatus status);
}