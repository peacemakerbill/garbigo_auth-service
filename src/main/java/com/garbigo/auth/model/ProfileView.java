package com.garbigo.auth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "profile_views")
@CompoundIndexes({
        // Fast lookup by viewed profile, sorted by time (used in all "who viewed me" queries)
        @CompoundIndex(name = "profile_view_idx", def = "{'viewedUserId': 1, 'viewedAt': -1}"),
        // Fast duplicate-prevention check (viewedUserId + viewerId + viewedAt)
        @CompoundIndex(name = "duplicate_check_idx", def = "{'viewedUserId': 1, 'viewerId': 1, 'viewedAt': -1}")
})
public class ProfileView {

    @Id
    private String id;

    private String viewedUserId;   // Profile being viewed
    private String viewerId;       // Who viewed it (null = anonymous)

    private String viewerIp;
    private String userAgent;

    @CreatedDate  // Requires @EnableMongoAuditing on your Spring config class
    private Instant viewedAt;

    @JsonProperty("isAnonymous")
    private boolean anonymous;
}