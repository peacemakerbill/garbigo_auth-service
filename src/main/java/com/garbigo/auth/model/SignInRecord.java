package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "sign_in_records")
@CompoundIndex(name = "user_time_idx", def = "{'userId': 1, 'occurredAt': -1}")
public class SignInRecord {

    @Id
    private String id;

    private String userId;
    private boolean success;
    private String failureReason;
    private String signInMethod;

    private String ip;
    private String locationCountry;
    private String locationRegion;
    private String locationCity;

    private String deviceId;
    private String sessionId;
    private String deviceLabel;
    private String deviceType;
    private String osName;
    private String osVersion;
    private String browserName;
    private String browserVersion;
    private String deviceStatus;

    private String riskLevel;
    private List<String> riskReasons;

    private boolean alertSent;
    private String alertFingerprint;

    @Indexed
    private Instant occurredAt;
}
