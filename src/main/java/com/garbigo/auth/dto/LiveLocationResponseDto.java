package com.garbigo.auth.dto;

import com.garbigo.auth.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class LiveLocationResponseDto {

    private String userId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String profilePictureUrl;
    private Role role;

    private double latitude;
    private double longitude;
    private Instant timestamp;
    private boolean active;
}