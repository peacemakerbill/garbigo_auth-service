package com.garbigo.auth.service;

import com.garbigo.auth.model.ApplicationStatus;
import com.garbigo.auth.model.CollectorApplication;
import com.garbigo.auth.model.CollectorApplication.ApplicationDocument;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ApplicationEmailService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEmailService.class);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.url}")
    private String appUrl;

    @Value("${collector-application.page-path:/collector/application}")
    private String pagePath;

    @Value("${collector-application.review-days:3}")
    private int reviewDays;

    public ApplicationEmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendStatusEmail(CollectorApplication app, ApplicationStatus status, String teamNote,
                                List<String> attentionItems) {
        Look look = lookFor(status);
        String subject;
        String heading;
        String intro;
        String noteTitle = "Note from our team";
        List<String> steps;
        String ctaLabel = "Open my application";

        switch (status) {
            case SUBMITTED -> {
                subject = "We received your collector application";
                heading = "We have your application";
                intro = "Thank you for applying to become a Garbigo collector. Your application is now with our team.";
                steps = List.of(
                        "We check your details and documents. This usually takes " + reviewDays + " working days.",
                        "If something is missing or unclear, we will email you exactly what to fix.",
                        "Once everything is verified, we approve your account as a collector.");
            }
            case PROCESSING -> {
                subject = "Your collector application is being reviewed";
                heading = "Review has started";
                intro = "A member of our team has started reviewing your application and documents.";
                steps = List.of(
                        "We check each document against the details you gave us.",
                        "You do not need to do anything right now.",
                        "We will email you as soon as there is news.");
            }
            case MORE_INFO_NEEDED -> {
                subject = "Action needed on your collector application";
                heading = "We need a little more from you";
                intro = "We could not finish reviewing your application yet. A few things need your attention.";
                noteTitle = "What we need";
                steps = List.of(
                        "Open your application and read the note from our team.",
                        "Update your details or replace the documents we mention.",
                        "Submit your application again. We will pick it up right away.");
                ctaLabel = "Fix my application";
            }
            case VERIFIED -> {
                subject = "Your collector application is verified";
                heading = "Your documents are verified";
                intro = "Good news. We have checked your details and documents and everything looks right.";
                steps = List.of(
                        "A team member completes the final approval.",
                        "Your account is then switched to a collector account.",
                        "You will get a confirmation email as soon as it happens.");
            }
            case ACCEPTED -> {
                subject = "Welcome aboard, you are now a Garbigo collector";
                heading = "You are now a collector";
                intro = "Congratulations. Your application has been approved and your account is now a collector account.";
                noteTitle = "Message from our team";
                steps = List.of(
                        "Sign in to the app. Your collector tools are now available.",
                        "Check your profile and keep your live location switched on while you work.",
                        "Start accepting collection requests from customers near you.");
                ctaLabel = "Go to Garbigo";
            }
            case REJECTED -> {
                subject = "An update on your collector application";
                heading = "We could not approve your application";
                intro = "Thank you for the time you put into applying. After reviewing your application, we are not able to approve it at this time.";
                noteTitle = "Reason";
                steps = List.of(
                        "Read the reason above carefully.",
                        "Fix the issue, for example by getting a missing certificate.",
                        "Start a new application when you are ready. We will be glad to look at it again.");
                ctaLabel = "View my application";
            }
            case WITHDRAWN -> {
                subject = "Your collector application was withdrawn";
                heading = "Application withdrawn";
                intro = "This confirms that your collector application has been withdrawn, as you asked.";
                steps = List.of(
                        "Your uploaded documents are no longer used for this application.",
                        "You can start a new application whenever you are ready.");
                ctaLabel = "Start a new application";
            }
            default -> {
                return;
            }
        }

        Context ctx = baseContext(app, look, heading, intro, steps, ctaLabel);
        ctx.setVariable("note", teamNote);
        ctx.setVariable("noteTitle", noteTitle);
        ctx.setVariable("items", attentionItems);
        ctx.setVariable("itemsTitle", "Documents to replace");
        ctx.setVariable("statusLabel", status.label());
        send(app.getApplicantEmail(), subject, ctx);
    }

    public void sendDocumentRejected(CollectorApplication app, ApplicationDocument doc) {
        Look look = lookFor(ApplicationStatus.MORE_INFO_NEEDED);
        Context ctx = baseContext(app, look,
                "A document needs replacing",
                "We looked at your " + doc.getType().label() + " and could not accept it.",
                List.of(
                        "Open your application and upload a new copy of this document.",
                        "Submit your application again when you are done."),
                "Replace my document");
        ctx.setVariable("note", doc.getReviewNote());
        ctx.setVariable("noteTitle", "Why we could not accept it");
        ctx.setVariable("items", List.of(doc.getType().label()));
        ctx.setVariable("itemsTitle", "Document to replace");
        ctx.setVariable("statusLabel", app.getStatus().label());
        send(app.getApplicantEmail(), "A document on your collector application needs replacing", ctx);
    }

    private Context baseContext(CollectorApplication app, Look look, String heading, String intro,
                                List<String> steps, String ctaLabel) {
        Context ctx = new Context();
        ctx.setVariable("name", firstName(app));
        ctx.setVariable("email", app.getApplicantEmail());
        ctx.setVariable("heading", heading);
        ctx.setVariable("preheader", intro);
        ctx.setVariable("intro", intro);
        ctx.setVariable("steps", steps);
        ctx.setVariable("reference", app.getReferenceNumber());
        ctx.setVariable("accent", look.accent());
        ctx.setVariable("accentSoft", look.soft());
        ctx.setVariable("symbol", look.symbol());
        ctx.setVariable("applyingFor", "Garbigo collector");
        ctx.setVariable("updatedAt", STAMP.format(Instant.now()));
        ctx.setVariable("sentAt", STAMP.format(Instant.now()));
        ctx.setVariable("ctaLabel", ctaLabel);
        ctx.setVariable("ctaUrl", appUrl + pagePath);
        return ctx;
    }

    private void send(String to, String subject, Context ctx) {
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Garbigo - " + subject);
            helper.setText(templateEngine.process("application-update", ctx), true);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("APPLICATION EMAIL: could not send '{}' to {}: {}", subject, to, e.getMessage());
        }
    }

    private static String firstName(CollectorApplication app) {
        String full = app.getApplicantName();
        if (full == null || full.isBlank()) {
            return "there";
        }
        return full.trim().split("\\s+")[0];
    }

    private record Look(String accent, String soft, String symbol) {
    }

    private static Look lookFor(ApplicationStatus status) {
        return switch (status) {
            case PROCESSING -> new Look("#1565c0", "#e3f2fd", "i");
            case MORE_INFO_NEEDED -> new Look("#ef6c00", "#fff3e0", "!");
            case REJECTED -> new Look("#c62828", "#ffebee", "×");
            case WITHDRAWN -> new Look("#546e7a", "#eceff1", "–");
            default -> new Look("#2e7d32", "#e8f5e9", "✓");
        };
    }
}