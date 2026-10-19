package com.garbigo.auth.service;

import com.garbigo.auth.enums.ApplicationStatus;
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

    @Value("${collector-application.staff-page-path:/admin/collector-applications}")
    private String staffPagePath;

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
                boolean canReapply = !Boolean.FALSE.equals(app.getReapplyAllowed());
                Instant availableAt = app.getReapplyAvailableAt();
                boolean waiting = canReapply && availableAt != null && availableAt.isAfter(Instant.now());
                subject = "Your collector application was not approved: what to correct";
                heading = "We could not approve your application";
                noteTitle = "Reason";
                if (canReapply) {
                    intro = "Thank you for the time you put into applying. We are not able to approve this application, "
                            + "but you are welcome to apply again. Below is exactly what to correct so your next application can succeed.";
                    steps = List.of(
                            "Read the reason and the list of corrections.",
                            "Fix each point. For documents, get a clear and valid copy.",
                            waiting
                                    ? "You can apply again from " + STAMP.format(availableAt)
                                    + ". We will copy your details and verified documents for you."
                                    : "Apply again from the app. We copy your details and verified documents, so you only fix what is listed.");
                    ctaLabel = waiting ? "View my application" : "Apply again";
                } else {
                    intro = "Thank you for the time you put into applying. After reviewing your application, we are not able to approve it, and this decision is final.";
                    steps = List.of(
                            "Read the reason above.",
                            "If you think this is a mistake, reply to this email and quote your reference number.");
                    ctaLabel = "View my application";
                }
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
        ctx.setVariable("itemsTitle", status == ApplicationStatus.REJECTED ? "What to correct"
                : status == ApplicationStatus.MORE_INFO_NEEDED ? "What to fix" : "Documents to replace");
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

    public void sendReapplicationStarted(CollectorApplication app, String previousReference,
                                         List<String> corrections, int carriedDocuments) {
        Look look = lookFor(ApplicationStatus.DRAFT);
        String intro = "You started a new collector application based on " + previousReference
                + ". We copied your details" + (carriedDocuments > 0
                ? " and " + carriedDocuments + " verified document" + (carriedDocuments == 1 ? "" : "s") : "")
                + " so you do not start from zero.";
        Context ctx = baseContext(app, look, "Your new application is ready", intro,
                List.of(
                        "Review the details we copied and update anything that has changed.",
                        "Fix the points listed here and upload any document that is missing.",
                        "Confirm the declarations and submit your application."),
                "Continue my application");
        ctx.setVariable("note", null);
        ctx.setVariable("noteTitle", null);
        ctx.setVariable("items", corrections == null || corrections.isEmpty() ? null : corrections);
        ctx.setVariable("itemsTitle", "Still to correct");
        ctx.setVariable("statusLabel", ApplicationStatus.DRAFT.label());
        send(app.getApplicantEmail(), "Your new collector application has started", ctx);
    }

    public void sendStaffResubmitted(CollectorApplication app, String to, String reviewerFirstName) {
        Look look = lookFor(ApplicationStatus.SUBMITTED);
        String applicant = app.getApplicantName() == null ? "An applicant" : app.getApplicantName();
        Context ctx = baseContext(app, look, "Application resubmitted",
                applicant + " resubmitted application " + app.getReferenceNumber()
                        + " after the changes you asked for.",
                List.of(
                        "Open the application and check that each correction was made.",
                        "Verify any document that was replaced.",
                        "Move the application on when you are done."),
                "Open the application");
        ctx.setVariable("name", reviewerFirstName == null || reviewerFirstName.isBlank() ? "there" : reviewerFirstName);
        ctx.setVariable("email", to);
        ctx.setVariable("ctaUrl", appUrl + staffPagePath + "/" + app.getId());
        ctx.setVariable("note", app.getLastPublicNote());
        ctx.setVariable("noteTitle", "Your earlier request");
        ctx.setVariable("items", app.getLastCorrections() == null || app.getLastCorrections().isEmpty() ? null : app.getLastCorrections());
        ctx.setVariable("itemsTitle", "Corrections requested");
        ctx.setVariable("statusLabel", ApplicationStatus.SUBMITTED.label());
        send(to, "Application " + app.getReferenceNumber() + " was resubmitted", ctx);
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
        ctx.setVariable("tone", look.tone());
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
            helper.setText(templateEngine.process("emails/application-update", ctx), true);
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

    private record Look(String tone) {
    }

    private static Look lookFor(ApplicationStatus status) {
        return switch (status) {
            case PROCESSING -> new Look("info");
            case MORE_INFO_NEEDED -> new Look("warning");
            case REJECTED -> new Look("danger");
            case WITHDRAWN -> new Look("neutral");
            default -> new Look("success");
        };
    }
}