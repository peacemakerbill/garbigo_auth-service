package com.garbigo.auth.dto;

import com.garbigo.auth.model.Role;
import lombok.Data;

@Data
public class SignupRequest {
    private String username;
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String homeAddress;
    private String password;
    private Role role;
}