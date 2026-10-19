package com.garbigo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

public final class SecurityDtos {

    private SecurityDtos() {
    }

    public record DeviceDto(
            String id,
            String label,
            String deviceType,
            String osName,
            String browserName,
            Instant firstSeenAt,
            Instant lastSeenAt,
            String lastIp,
            String lastLocation,
            int signInCount,
            boolean trusted,
            Instant trustedAt,
            boolean current,
            int activeSessions) {
    }

    public record SessionDto(
            String id,
            String deviceId,
            String deviceLabel,
            Instant createdAt,
            Instant lastSeenAt,
            Instant expiresAt,
            String ip,
            String location,
            String signInMethod,
            boolean current) {
    }

    public record SignInActivityDto(
            String id,
            boolean success,
            String failureReason,
            Instant occurredAt,
            String signInMethod,
            String ip,
            String location,
            String deviceLabel,
            String deviceStatus,
            String riskLevel) {
    }

    public record DeviceDetailDto(DeviceDto device, java.util.List<SignInActivityDto> recentSignIns) {
    }

    public record SecurityEventDto(
            String id,
            String type,
            String userId,
            String actorId,
            String ip,
            String detail,
            Instant occurredAt) {
    }

    public record CountDto(String message, int count) {
    }

    public record ActionPreviewDto(
            Instant occurredAt,
            String deviceLabel,
            String location,
            String maskedIp,
            String signInMethod) {
    }

    public record ActionResultDto(
            String message,
            int sessionsEnded,
            boolean deviceRemoved,
            boolean passwordResetEmailSent) {
    }

    @Data
    public static class ReauthRequest {
        @Size(max = 200, message = "That password is too long.")
        private String password;
    }

    @Data
    public static class ActionTokenRequest {
        @NotBlank(message = "The link is missing its token.")
        @Size(max = 100, message = "The link is not valid.")
        private String token;

        private Boolean requestPasswordReset;
    }
}
