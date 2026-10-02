package com.garbigo.auth.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.garbigo.auth.model.Role;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "id", "username", "firstName", "middleName", "lastName",
        "email", "phoneNumber", "homeAddress", "profilePictureUrl",
        "role", "wastePreferences", "collectionSchedule",
        "verified", "active", "archived"
})
public class UserDto {
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
}