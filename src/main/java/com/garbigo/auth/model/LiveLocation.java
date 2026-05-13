package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "live_locations")
public class LiveLocation {

    @Id
    private String id;

    private String userId;
    private double latitude;
    private double longitude;

    @CreatedDate
    private Instant timestamp;

    public LiveLocation() {
        // MongoDB will handle timestamp
    }
}