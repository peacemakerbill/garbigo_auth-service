package com.garbigo.auth.model;

import com.garbigo.auth.enums.SecurityActionType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "security_action_tokens")
public class SecurityActionToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String userId;

    private SecurityActionType type;
    private String sessionId;
    private String deviceId;
    private String signInRecordId;

    private Instant createdAt;

    @Indexed
    private Instant expiresAt;

    private Instant usedAt;
}
