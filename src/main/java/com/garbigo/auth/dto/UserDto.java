package com.garbigo.auth.dto;

import com.garbigo.auth.model.Role;
import lombok.Data;

@Data
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
    private boolean verified;
    private boolean active;
    private boolean archived;
}