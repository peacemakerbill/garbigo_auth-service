package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CurrentLocationDto {
    private String userId;
    private double latitude;
    private double longitude;
    private Instant timestamp;
}