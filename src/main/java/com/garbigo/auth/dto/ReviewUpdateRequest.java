package com.garbigo.auth.dto;

import lombok.Data;

@Data
public class ReviewUpdateRequest {
    private int rating;
    private String comment;
}