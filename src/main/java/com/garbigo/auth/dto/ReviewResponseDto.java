package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ReviewResponseDto {
    private String id;
    private String reviewerId;
    private String reviewerName;           // Full name: "John Doe"
    private String reviewerProfilePictureUrl;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
}