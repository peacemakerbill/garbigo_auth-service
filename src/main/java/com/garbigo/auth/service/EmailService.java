package com.garbigo.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.url}")
    private String appUrl;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendVerificationEmail(String to, String name, String token, Instant expiresAt) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Garbigo - Verify Your Account");

        Context context = new Context();
        context.setVariable("appUrl", appUrl);
        context.setVariable("token", token);
        context.setVariable("name", name);
        context.setVariable("email", to);
        context.setVariable("sentAt", TIMESTAMP_FORMAT.format(Instant.now()));
        context.setVariable("expiresAt", TIMESTAMP_FORMAT.format(expiresAt));

        String htmlContent = templateEngine.process("verify-account", context);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    public void sendResetPasswordEmail(String to, String name, String token, Instant expiresAt) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Garbigo - Reset Your Password");

        Context context = new Context();
        context.setVariable("appUrl", appUrl);
        context.setVariable("token", token);
        context.setVariable("name", name);
        context.setVariable("email", to);
        context.setVariable("sentAt", TIMESTAMP_FORMAT.format(Instant.now()));
        context.setVariable("expiresAt", TIMESTAMP_FORMAT.format(expiresAt));

        String htmlContent = templateEngine.process("reset-password", context);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}