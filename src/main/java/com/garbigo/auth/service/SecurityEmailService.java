package com.garbigo.auth.service;

import com.garbigo.auth.config.SecurityProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class SecurityEmailService {

    public enum EmailEvent {
        NEW_SIGN_IN,
        UNTRUSTED_SIGN_IN,
        SUSPICIOUS_SIGN_IN,
        ADMIN_INVITATION,
        ADMIN_ROLE_GRANTED,
        ROLE_CHANGED,
        PASSWORD_CHANGED,
        PASSWORD_RESET_COMPLETED,
        EMAIL_CHANGED,
        ACCOUNT_LOCKED,
        UNAUTHORIZED_REPORT_COMPLETED,
        TOKEN_REPLAY,
        ACCOUNT_ALREADY_EXISTS
    }

    private static final String TEMPLATE = "emails/security-notice";

    private static final Map<EmailEvent, String> SUBJECTS = new EnumMap<>(EmailEvent.class);

    static {
        SUBJECTS.put(EmailEvent.NEW_SIGN_IN, "Garbigo - New sign-in to your account");
        SUBJECTS.put(EmailEvent.UNTRUSTED_SIGN_IN, "Garbigo - Sign-in to your account");
        SUBJECTS.put(EmailEvent.SUSPICIOUS_SIGN_IN, "Garbigo - Unusual sign-in activity on your account");
        SUBJECTS.put(EmailEvent.ADMIN_INVITATION, "Garbigo - You have been invited as an administrator");
        SUBJECTS.put(EmailEvent.ADMIN_ROLE_GRANTED, "Garbigo - Administrator access added to your account");
        SUBJECTS.put(EmailEvent.ROLE_CHANGED, "Garbigo - Your account access was changed");
        SUBJECTS.put(EmailEvent.PASSWORD_CHANGED, "Garbigo - Your password was changed");
        SUBJECTS.put(EmailEvent.PASSWORD_RESET_COMPLETED, "Garbigo - Your password was reset");
        SUBJECTS.put(EmailEvent.EMAIL_CHANGED, "Garbigo - The email address on your account was changed");
        SUBJECTS.put(EmailEvent.ACCOUNT_LOCKED, "Garbigo - Too many failed sign-in attempts");
        SUBJECTS.put(EmailEvent.UNAUTHORIZED_REPORT_COMPLETED, "Garbigo - Your account has been secured");
        SUBJECTS.put(EmailEvent.TOKEN_REPLAY, "Garbigo - A signed-out session tried to access your account");
        SUBJECTS.put(EmailEvent.ACCOUNT_ALREADY_EXISTS, "Garbigo - Someone tried to register with your email");
    }

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer renderer;
    private final SecurityProperties properties;

    @Value("${app.url}")
    private String appUrl;

    public SecurityEmailService(JavaMailSender mailSender, EmailTemplateRenderer renderer,
                                SecurityProperties properties) {
        this.mailSender = mailSender;
        this.renderer = renderer;
        this.properties = properties;
    }

    public Map<String, Object> variables(EmailEvent event, String name, String email) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("event", event.name());
        vars.put("subject", SUBJECTS.get(event));
        vars.put("appName", "Garbigo");
        vars.put("name", name == null || name.isBlank() ? "there" : name);
        vars.put("email", email);
        vars.put("sentAt", formatTime(Instant.now()));
        vars.put("year", String.valueOf(java.time.Year.now(ZoneOffset.UTC).getValue()));
        vars.put("reviewUrl", appUrl + properties.getSecurityReviewPath());
        return vars;
    }

    public String formatTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z", Locale.ENGLISH)
                .withZone(zone())
                .format(instant);
    }

    public String reportUrl(String rawToken) {
        return appUrl + properties.getReportPath() + "?token=" + rawToken;
    }

    public String inviteUrl() {
        return appUrl + properties.getAdminRegisterPath();
    }

    public void send(String to, EmailEvent event, Map<String, Object> variables) throws MessagingException {
        EmailTemplateRenderer.Rendered rendered = renderer.render(TEMPLATE, variables);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(SUBJECTS.get(event));
        helper.setText(rendered.text(), rendered.html());
        mailSender.send(message);
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(properties.getDisplayTimeZone());
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }
}
