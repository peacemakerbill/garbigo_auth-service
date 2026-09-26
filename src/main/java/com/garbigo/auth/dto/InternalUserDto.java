package com.garbigo.auth.dto;

import com.garbigo.auth.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class InternalUserDto {
    private String id;
    private String username;
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String homeAddress;
    private String profilePictureUrl;
    private Role role;
    private String wastePreferences;
    private String collectionSchedule;
    private boolean verified;
    private boolean active;
    private boolean archived;
    private List<String> followers;
    private List<String> likes;
    private List<String> reviews;
    private List<String> liveLocations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}