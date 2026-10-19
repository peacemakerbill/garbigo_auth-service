package com.garbigo.auth.repository;

import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.model.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface SecurityEventRepository extends MongoRepository<SecurityEvent, String> {

    boolean existsByTypeAndSubjectHashAndOccurredAtAfter(SecurityEventType type, String subjectHash, Instant after);

    boolean existsByTypeAndUserIdAndOccurredAtAfter(SecurityEventType type, String userId, Instant after);

    Page<SecurityEvent> findByUserId(String userId, Pageable pageable);

    Page<SecurityEvent> findByType(SecurityEventType type, Pageable pageable);

    Page<SecurityEvent> findByUserIdAndType(String userId, SecurityEventType type, Pageable pageable);

    long deleteByOccurredAtBefore(Instant cutoff);
}
