package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Data
@Document(collection = "user_devices")
@CompoundIndex(name = "user_lastSeen_idx", def = "{'userId': 1, 'lastSeenAt': -1}")
public class UserDevice {

    @Id
    private String id;

    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    private String label;
    private String deviceType;
    private String osName;
    private String browserName;

    private Instant firstSeenAt;

    @Indexed
    private Instant lastSeenAt;

    private String lastIp;
    private String lastLocation;
    private int signInCount;

    private boolean trusted;
    private Instant trustedAt;
    private Instant trustRevokedAt;
}
