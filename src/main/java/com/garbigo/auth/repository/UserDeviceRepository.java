package com.garbigo.auth.repository;

import com.garbigo.auth.model.UserDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends MongoRepository<UserDevice, String> {

    Optional<UserDevice> findByTokenHash(String tokenHash);

    Optional<UserDevice> findByIdAndUserId(String id, String userId);

    List<UserDevice> findByUserIdOrderByLastSeenAtDesc(String userId);

    long countByUserId(String userId);

    long deleteByLastSeenAtBefore(Instant cutoff);
}
