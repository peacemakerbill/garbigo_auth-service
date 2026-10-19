package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "user_sessions")
@CompoundIndex(name = "user_revoked_idx", def = "{'userId': 1, 'revokedAt': 1}")
public class UserSession {

    @Id
    private String id;

    @Indexed(unique = true)
    private String jti;

    private String userId;

    @Indexed
    private String deviceId;

    private Instant createdAt;
    private Instant lastSeenAt;

    @Indexed
    private Instant expiresAt;

    private String ip;
    private String location;
    private String deviceLabel;
    private String signInMethod;

    private Instant revokedAt;
    private String revokeReason;
}
