package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ProfileViewDto {
    private String viewerId;
    private String viewerName;
    private String viewerProfilePictureUrl;
    private Instant viewedAt;
    private boolean isAnonymous;
}