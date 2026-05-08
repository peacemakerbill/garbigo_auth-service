package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "follows")
@CompoundIndex(name = "follow_idx", def = "{'userId': 1, 'followerId': 1}", unique = true)
public class Follow {
    private String id;
    private String userId;        // Being followed
    private String followerId;    // Follower

    @CreatedDate
    private LocalDateTime createdAt;
}