package com.garbigo.auth.service;

import com.garbigo.auth.enums.SignInMethod;
import com.garbigo.auth.model.SignInRecord;
import com.garbigo.auth.model.User;
import com.garbigo.auth.model.UserSession;
import com.garbigo.auth.repository.SignInRecordRepository;
import com.garbigo.auth.security.ClientContext;
import com.garbigo.auth.security.JwtUtil;
import com.garbigo.auth.security.LoginThrottle;
import com.garbigo.auth.util.EmailAddresses;
import com.garbigo.auth.util.ParsedUserAgent;
import com.garbigo.auth.util.UserAgentParser;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SignInSecurityService {

    public record Result(String newDeviceToken) {
    }

    private final DeviceService deviceService;
    private final SessionService sessionService;
    private final SignInRecordRepository signIns;
    private final LoginThrottle throttle;
    private final SecurityNotificationService notifications;

    public SignInSecurityService(DeviceService deviceService, SessionService sessionService,
                                 SignInRecordRepository signIns, LoginThrottle throttle,
                                 SecurityNotificationService notifications) {
        this.deviceService = deviceService;
        this.sessionService = sessionService;
        this.signIns = signIns;
        this.throttle = throttle;
        this.notifications = notifications;
    }

    public Result completeSignIn(User user, JwtUtil.GeneratedToken token, SignInMethod method, ClientContext ctx) {
        DeviceService.Recognition recognition = deviceService.recognize(user, ctx);
        UserSession session = sessionService.create(user, token, recognition.device(), ctx, method);
        ParsedUserAgent agent = UserAgentParser.parse(ctx.userAgent());

        SignInRecord record = baseRecord(user, ctx, method, agent);
        record.setSuccess(true);
        record.setDeviceId(recognition.device().getId());
        record.setSessionId(session.getId());
        record.setDeviceStatus(recognition.status().name());
        record = signIns.save(record);

        throttle.recordSuccess(emailKey(user.getEmail()), ctx.ip());
        notifications.afterSignIn(record.getId());

        return new Result(recognition.issuedToken());
    }

    public void recordFailure(User user, SignInMethod method, String reason, ClientContext ctx) {
        if (user == null) {
            return;
        }
        SignInRecord record = baseRecord(user, ctx, method, UserAgentParser.parse(ctx.userAgent()));
        record.setSuccess(false);
        record.setFailureReason(reason);
        signIns.save(record);
    }

    public static String emailKey(String email) {
        String normalized = EmailAddresses.normalize(email);
        return normalized == null ? "" : normalized;
    }

    private SignInRecord baseRecord(User user, ClientContext ctx, SignInMethod method, ParsedUserAgent agent) {
        SignInRecord record = new SignInRecord();
        record.setUserId(user.getId());
        record.setSignInMethod(method == null ? null : method.name());
        record.setIp(ctx.ip());
        record.setDeviceLabel(agent.label());
        record.setDeviceType(agent.deviceType());
        record.setOsName(agent.osName());
        record.setOsVersion(agent.osVersion());
        record.setBrowserName(agent.browserName());
        record.setBrowserVersion(agent.browserVersion());
        record.setOccurredAt(Instant.now());
        return record;
    }
}
