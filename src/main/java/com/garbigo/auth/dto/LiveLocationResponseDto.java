package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class LiveLocationResponseDto {

    private String userId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String profilePictureUrl;

    private double latitude;
    private double longitude;
    private Instant timestamp;
    private boolean active;
}