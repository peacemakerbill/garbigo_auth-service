package com.garbigo.auth.service;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.enums.DeviceStatus;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SessionRevokeReason;
import com.garbigo.auth.exception.ApiException;
import com.garbigo.auth.model.User;
import com.garbigo.auth.model.UserDevice;
import com.garbigo.auth.repository.UserDeviceRepository;
import com.garbigo.auth.security.ClientContext;
import com.garbigo.auth.util.ParsedUserAgent;
import com.garbigo.auth.util.SecureTokens;
import com.garbigo.auth.util.UserAgentParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    public record Recognition(UserDevice device, DeviceStatus status, String issuedToken) {
    }

    private final UserDeviceRepository devices;
    private final SessionService sessionService;
    private final SecurityProperties properties;
    private final SecurityAuditService audit;

    public DeviceService(UserDeviceRepository devices, SessionService sessionService, SecurityProperties properties,
                         SecurityAuditService audit) {
        this.devices = devices;
        this.sessionService = sessionService;
        this.properties = properties;
        this.audit = audit;
    }

    public Recognition recognize(User user, ClientContext ctx) {
        ParsedUserAgent agent = UserAgentParser.parse(ctx.userAgent());
        Instant now = Instant.now();

        if (ctx.deviceToken() != null) {
            Optional<UserDevice> known = devices.findByTokenHash(SecureTokens.sha256Hex(ctx.deviceToken()));
            if (known.isPresent() && user.getId().equals(known.get().getUserId())) {
                UserDevice device = known.get();
                device.setLastSeenAt(now);
                device.setLastIp(ctx.ip());
                device.setSignInCount(device.getSignInCount() + 1);
                applyHints(device, agent);
                devices.save(device);

                DeviceStatus status = device.isTrusted() && properties.isDeviceTrustEnabled()
                        ? DeviceStatus.TRUSTED : DeviceStatus.RECOGNIZED;
                return new Recognition(device, status, null);
            }
        }

        makeRoom(user.getId());

        String token = SecureTokens.generate();
        UserDevice device = new UserDevice();
        device.setUserId(user.getId());
        device.setTokenHash(SecureTokens.sha256Hex(token));
        device.setFirstSeenAt(now);
        device.setLastSeenAt(now);
        device.setLastIp(ctx.ip());
        device.setSignInCount(1);
        applyHints(device, agent);
        device = devices.save(device);

        return new Recognition(device, DeviceStatus.NEW, token);
    }

    public List<UserDevice> list(String userId) {
        return devices.findByUserIdOrderByLastSeenAtDesc(userId);
    }

    public UserDevice get(String userId, String deviceId) {
        return devices.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "We couldn't find that device."));
    }

    public UserDevice trust(String userId, String deviceId) {
        if (!properties.isDeviceTrustEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Trusting devices is turned off.");
        }
        UserDevice device = get(userId, deviceId);
        device.setTrusted(true);
        device.setTrustedAt(Instant.now());
        device.setTrustRevokedAt(null);
        devices.save(device);
        audit.record(SecurityEventType.DEVICE_TRUSTED, userId, userId, "device=" + device.getId());
        return device;
    }

    public UserDevice revokeTrust(String userId, String deviceId) {
        UserDevice device = get(userId, deviceId);
        if (device.isTrusted()) {
            device.setTrusted(false);
            device.setTrustRevokedAt(Instant.now());
            devices.save(device);
            audit.record(SecurityEventType.DEVICE_TRUST_REVOKED, userId, userId, "device=" + device.getId());
        }
        return device;
    }

    public int remove(String userId, String deviceId) {
        UserDevice device = get(userId, deviceId);
        return removeDevice(device, SessionRevokeReason.DEVICE_REMOVED);
    }

    public boolean removeIfPresent(String userId, String deviceId) {
        if (deviceId == null) {
            return false;
        }
        Optional<UserDevice> device = devices.findByIdAndUserId(deviceId, userId);
        device.ifPresent(d -> removeDevice(d, SessionRevokeReason.UNAUTHORIZED_SIGN_IN_REPORTED));
        return device.isPresent();
    }

    public int revokeAllRemembered(String userId) {
        int count = 0;
        for (UserDevice device : devices.findByUserIdOrderByLastSeenAtDesc(userId)) {
            device.setTokenHash(SecureTokens.sha256Hex(SecureTokens.generate()));
            device.setTrusted(false);
            device.setTrustRevokedAt(Instant.now());
            devices.save(device);
            count++;
        }
        audit.record(SecurityEventType.REMEMBERED_DEVICES_REVOKED, userId, userId, "count=" + count);
        return count;
    }

    public void updateLocation(String deviceId, String location) {
        if (deviceId == null || location == null) {
            return;
        }
        devices.findById(deviceId).ifPresent(device -> {
            device.setLastLocation(location);
            devices.save(device);
        });
    }

    private int removeDevice(UserDevice device, SessionRevokeReason reason) {
        int ended = sessionService.revokeByDevice(device.getUserId(), device.getId(), reason);
        devices.delete(device);
        audit.record(SecurityEventType.DEVICE_REMOVED, device.getUserId(), device.getUserId(),
                "device=" + device.getId() + ", sessionsEnded=" + ended);
        return ended;
    }

    private void applyHints(UserDevice device, ParsedUserAgent agent) {
        device.setLabel(agent.label());
        device.setDeviceType(agent.deviceType());
        device.setOsName(agent.osName());
        device.setBrowserName(agent.browserName());
    }

    private void makeRoom(String userId) {
        if (devices.countByUserId(userId) < properties.getMaxDevicesPerUser()) {
            return;
        }
        List<UserDevice> all = devices.findByUserIdOrderByLastSeenAtDesc(userId);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (!all.get(i).isTrusted()) {
                devices.delete(all.get(i));
                return;
            }
        }
        if (!all.isEmpty()) {
            devices.delete(all.get(all.size() - 1));
        }
    }
}
