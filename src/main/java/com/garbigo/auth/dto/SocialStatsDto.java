package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SocialStatsDto {
    private long followersCount;
    private long followingCount;
    private long likesCount;
    private double averageRating;
}