package com.garbigo.auth.dto;

import lombok.Data;

@Data
public class ProfileUpdateDto {
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String homeAddress;
    private String wastePreferences;
    private String collectionSchedule;
}