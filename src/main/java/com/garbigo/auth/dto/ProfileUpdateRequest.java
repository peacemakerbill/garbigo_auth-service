package com.garbigo.auth.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ProfileUpdateRequest {
    private String firstName;
    private String middleName;
    private String lastName;
    private String phoneNumber;
    private String homeAddress;
    private String wastePreferences;
    private String collectionSchedule;
    private MultipartFile profilePicture;
}