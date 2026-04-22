package com.garbigo.auth.repository;

import com.garbigo.auth.model.LiveLocation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LiveLocationRepository extends MongoRepository<LiveLocation, String> {
    
    // Get latest location for a user
    List<LiveLocation> findByUserIdOrderByTimestampDesc(String userId);

    // Optional: Get recent locations for route tracking
    List<LiveLocation> findByUserIdAndTimestampAfter(String userId, Instant since);
}