package com.garbigo.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.url}")
    private String appUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String to, String token) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Garbigo - Verify Your Account");

        String htmlContent = """
                <html>
                <body style="font-family: Arial, sans-serif; background:#f4f4f4; padding:20px;">
                  <div style="max-width:600px; margin:auto; background:white; padding:30px; border-radius:10px;">
                    <h1 style="color:#2e7d32;">Welcome to Garbigo!</h1>
                    <p>Please verify your account by clicking the button below:</p>
                    <a href="%s/auth/verify?token=%s" style="display:inline-block; padding:12px 24px; background:#2e7d32; color:white; text-decoration:none; border-radius:5px;">Verify Account</a>
                    <p style="margin-top:30px; color:#666;">If you didn't sign up, ignore this email.</p>
                  </div>
                </body>
                </html>
                """.formatted(appUrl, token);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    public void sendResetPasswordEmail(String to, String token) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Garbigo - Reset Your Password");

        String htmlContent = """
                <html>
                <body style="font-family: Arial, sans-serif; background:#f4f4f4; padding:20px;">
                  <div style="max-width:600px; margin:auto; background:white; padding:30px; border-radius:10px;">
                    <h1 style="color:#d32f2f;">Password Reset Request</h1>
                    <p>Click the button below to reset your password:</p>
                    <a href="%s/auth/reset-password/confirm?token=%s" style="display:inline-block; padding:12px 24px; background:#d32f2f; color:white; text-decoration:none; border-radius:5px;">Reset Password</a>
                    <p style="margin-top:30px; color:#666;">This link expires in 1 hour.</p>
                  </div>
                </body>
                </html>
                """.formatted(appUrl, token);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}