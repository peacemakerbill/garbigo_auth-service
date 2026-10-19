package com.garbigo.auth.model;

import com.garbigo.auth.enums.SecurityEventType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "security_events")
@CompoundIndex(name = "type_subject_time_idx", def = "{'type': 1, 'subjectHash': 1, 'occurredAt': -1}")
public class SecurityEvent {

    @Id
    private String id;

    private SecurityEventType type;

    @Indexed
    private String userId;

    private String actorId;
    private String subjectHash;
    private String ip;
    private String detail;

    @Indexed
    private Instant occurredAt;
}
