package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSummaryDto {
	private String id;
    private String username;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String profilePictureUrl;
    private String email;
    private String phoneNumber;
    private String role;
    private boolean active;

    private CurrentLocationDto currentLocation;
}