package com.garbigo.auth.service;

import com.garbigo.auth.enums.ApplicationStatus;
import com.garbigo.auth.model.CollectorApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class ReapplyPolicy {

    public record Decision(boolean applicable, boolean allowed, Instant availableAt, String message) {

        static Decision notApplicable() {
            return new Decision(false, false, null, null);
        }

        static Decision allowed(String message) {
            return new Decision(true, true, null, message);
        }

        static Decision blocked(Instant availableAt, String message) {
            return new Decision(true, false, availableAt, message);
        }
    }

    private final int cooldownDays;
    private final int maxAttempts;

    public ReapplyPolicy(@Value("${collector-application.reapply-cooldown-days:0}") int cooldownDays,
                         @Value("${collector-application.max-attempts:0}") int maxAttempts) {
        this.cooldownDays = Math.max(0, cooldownDays);
        this.maxAttempts = Math.max(0, maxAttempts);
    }

    public Instant availableAfter(Instant decidedAt) {
        if (decidedAt == null || cooldownDays == 0) {
            return null;
        }
        return decidedAt.plus(Duration.ofDays(cooldownDays));
    }

    public Decision evaluate(CollectorApplication app, Instant now) {
        if (app == null) {
            return Decision.notApplicable();
        }
        ApplicationStatus status = app.getStatus();
        if (status != ApplicationStatus.REJECTED && status != ApplicationStatus.WITHDRAWN) {
            return Decision.notApplicable();
        }

        if (status == ApplicationStatus.REJECTED && Boolean.FALSE.equals(app.getReapplyAllowed())) {
            return Decision.blocked(null,
                    "This decision is final, so a new application can't be started from this account. "
                            + "If you think this is a mistake, please contact our support team.");
        }

        if (maxAttempts > 0 && app.getAttemptNumber() >= maxAttempts) {
            return Decision.blocked(null, "You have reached the maximum of " + maxAttempts
                    + " applications. Please contact our support team if you need help.");
        }

        Instant availableAt = app.getReapplyAvailableAt();
        if (status == ApplicationStatus.REJECTED && availableAt != null && now.isBefore(availableAt)) {
            long days = Math.max(1, Duration.between(now, availableAt).toDays() + 1);
            return Decision.blocked(availableAt, "You can apply again in " + days + (days == 1 ? " day" : " days")
                    + ". Use the time to fix the points listed in our note.");
        }

        return Decision.allowed(status == ApplicationStatus.REJECTED
                ? "Your last application was not approved. Fix the points in our note, then apply again. "
                + "We will copy your details and your verified documents so you do not start from zero."
                : "You withdrew your last application. You can start a new one any time, and we will copy your details.");
    }
}