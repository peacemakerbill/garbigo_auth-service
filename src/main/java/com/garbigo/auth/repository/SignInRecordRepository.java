package com.garbigo.auth.repository;

import com.garbigo.auth.model.SignInRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface SignInRecordRepository extends MongoRepository<SignInRecord, String> {

    List<SignInRecord> findByUserId(String userId, Pageable pageable);

    List<SignInRecord> findByUserIdAndDeviceId(String userId, String deviceId, Pageable pageable);

    List<SignInRecord> findByUserIdAndOccurredAtAfter(String userId, Instant after);

    List<SignInRecord> findTop10ByUserIdAndSuccessTrueOrderByOccurredAtDesc(String userId);

    long countByUserIdAndSuccessFalseAndOccurredAtAfter(String userId, Instant after);

    boolean existsByUserIdAndAlertFingerprintAndAlertSentTrueAndOccurredAtAfter(String userId, String alertFingerprint,
                                                                                 Instant after);

    long deleteByOccurredAtBefore(Instant cutoff);
}
