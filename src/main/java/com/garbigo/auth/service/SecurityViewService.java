package com.garbigo.auth.service;

import com.garbigo.auth.dto.SecurityDtos.DeviceDetailDto;
import com.garbigo.auth.dto.SecurityDtos.DeviceDto;
import com.garbigo.auth.dto.SecurityDtos.SecurityEventDto;
import com.garbigo.auth.dto.SecurityDtos.SessionDto;
import com.garbigo.auth.dto.SecurityDtos.SignInActivityDto;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.model.SecurityEvent;
import com.garbigo.auth.model.SignInRecord;
import com.garbigo.auth.model.UserDevice;
import com.garbigo.auth.model.UserSession;
import com.garbigo.auth.repository.SecurityEventRepository;
import com.garbigo.auth.repository.SignInRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SecurityViewService {

    private static final int MAX_ACTIVITY = 100;
    private static final int DETAIL_ACTIVITY = 20;
    private static final int MAX_EVENT_PAGE = 100;

    private final DeviceService deviceService;
    private final SessionService sessionService;
    private final SignInRecordRepository signIns;
    private final SecurityEventRepository events;

    public SecurityViewService(DeviceService deviceService, SessionService sessionService,
                               SignInRecordRepository signIns, SecurityEventRepository events) {
        this.deviceService = deviceService;
        this.sessionService = sessionService;
        this.signIns = signIns;
        this.events = events;
    }

    public List<DeviceDto> devices(String userId, String currentDeviceId) {
        Map<String, Integer> active = activeSessionsByDevice(userId);
        return deviceService.list(userId).stream()
                .map(d -> toDto(d, currentDeviceId, active.getOrDefault(d.getId(), 0)))
                .toList();
    }

    public DeviceDetailDto deviceDetail(String userId, String deviceId, String currentDeviceId) {
        UserDevice device = deviceService.get(userId, deviceId);
        Map<String, Integer> active = activeSessionsByDevice(userId);
        List<SignInActivityDto> recent = signIns.findByUserIdAndDeviceId(userId, deviceId, newestFirst(DETAIL_ACTIVITY))
                .stream().map(SecurityViewService::toDto).toList();
        return new DeviceDetailDto(toDto(device, currentDeviceId, active.getOrDefault(device.getId(), 0)), recent);
    }

    public DeviceDto deviceSummary(String userId, UserDevice device, String currentDeviceId) {
        return toDto(device, currentDeviceId, activeSessionsByDevice(userId).getOrDefault(device.getId(), 0));
    }

    public List<SessionDto> sessions(String userId, String currentJti) {
        return sessionService.listActive(userId).stream()
                .map(s -> new SessionDto(s.getId(), s.getDeviceId(), s.getDeviceLabel(), s.getCreatedAt(),
                        s.getLastSeenAt(), s.getExpiresAt(), s.getIp(), s.getLocation(), s.getSignInMethod(),
                        currentJti != null && currentJti.equals(s.getJti())))
                .toList();
    }

    public List<SignInActivityDto> activity(String userId, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_ACTIVITY));
        return signIns.findByUserId(userId, newestFirst(size)).stream().map(SecurityViewService::toDto).toList();
    }

    public Page<SecurityEventDto> events(String userId, SecurityEventType type, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_EVENT_PAGE)),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<SecurityEvent> found;
        if (userId != null && type != null) {
            found = events.findByUserIdAndType(userId, type, request);
        } else if (userId != null) {
            found = events.findByUserId(userId, request);
        } else if (type != null) {
            found = events.findByType(type, request);
        } else {
            found = events.findAll(request);
        }
        return found.map(e -> new SecurityEventDto(e.getId(), e.getType() == null ? null : e.getType().name(),
                e.getUserId(), e.getActorId(), e.getIp(), e.getDetail(), e.getOccurredAt()));
    }

    private Map<String, Integer> activeSessionsByDevice(String userId) {
        Map<String, Integer> counts = new HashMap<>();
        for (UserSession session : sessionService.listActive(userId)) {
            if (session.getDeviceId() != null) {
                counts.merge(session.getDeviceId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static PageRequest newestFirst(int size) {
        return PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
    }

    private static DeviceDto toDto(UserDevice d, String currentDeviceId, int activeSessions) {
        return new DeviceDto(d.getId(), d.getLabel(), d.getDeviceType(), d.getOsName(), d.getBrowserName(),
                d.getFirstSeenAt(), d.getLastSeenAt(), d.getLastIp(), d.getLastLocation(), d.getSignInCount(),
                d.isTrusted(), d.getTrustedAt(), d.getId() != null && d.getId().equals(currentDeviceId),
                activeSessions);
    }

    private static SignInActivityDto toDto(SignInRecord r) {
        return new SignInActivityDto(r.getId(), r.isSuccess(), r.getFailureReason(), r.getOccurredAt(),
                r.getSignInMethod(), r.getIp(), joinLocation(r), r.getDeviceLabel(), r.getDeviceStatus(),
                r.getRiskLevel());
    }

    private static String joinLocation(SignInRecord record) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[] {record.getLocationCity(), record.getLocationRegion(),
                record.getLocationCountry()}) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(part);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
