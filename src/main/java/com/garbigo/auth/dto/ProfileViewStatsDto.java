package com.garbigo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ProfileViewStatsDto {
    private long totalViews;
    private long uniqueViewers;
    private long todayViews;
    private List<ProfileViewDto> recentViewers;
}