package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ReviewResponseDto {
    private String id;
    private String userId;
    private String username;           // Reviewer's name
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
}