package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "reviews")
@CompoundIndex(name = "review_idx", def = "{'userId': 1, 'targetId': 1, 'targetType': 1}")
public class Review {
    private String id;
    private String userId;
    private String targetId;
    private String targetType;
    private int rating;
    private String comment;

    @CreatedDate
    private LocalDateTime createdAt;
}