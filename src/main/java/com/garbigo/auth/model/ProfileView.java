package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "profile_views")
@CompoundIndex(name = "profile_view_idx", def = "{'viewedUserId': 1, 'viewerId': 1, 'viewedAt': -1}")
public class ProfileView {

    @Id
    private String id;

    private String viewedUserId;   // Profile owner
    private String viewerId;       // Who viewed (can be null for anonymous)

    private String viewerIp;       // For anonymous tracking
    private String userAgent;

    @CreatedDate
    private Instant viewedAt;

    private boolean isAnonymous;
}