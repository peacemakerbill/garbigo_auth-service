package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSummaryDto {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
}