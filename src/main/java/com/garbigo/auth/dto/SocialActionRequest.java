package com.garbigo.auth.dto;

import lombok.Data;

@Data
public class SocialActionRequest {
    private String targetId;
    private String targetType;
    private int rating;
    private String comment;
}