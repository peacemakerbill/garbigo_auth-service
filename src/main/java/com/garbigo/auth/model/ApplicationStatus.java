package com.garbigo.auth.model;

import java.util.EnumSet;
import java.util.Set;

public enum ApplicationStatus {

    DRAFT("Draft",
            "Your application is saved but not sent yet. Finish the steps and submit it when you are ready."),
    SUBMITTED("Submitted",
            "We have received your application. A team member will start reviewing it soon."),
    PROCESSING("In review",
            "Our team is checking your details and documents right now."),
    MORE_INFO_NEEDED("More information needed",
            "We need something from you before we can continue. Read the note from our team, make the changes, and submit again."),
    VERIFIED("Verified",
            "Your details and documents are verified. We are completing the final approval."),
    ACCEPTED("Accepted",
            "Congratulations. Your application is approved and your account is now a collector account."),
    REJECTED("Not approved",
            "We could not approve this application. Read the note from our team to see why. You can apply again once it is sorted out."),
    WITHDRAWN("Withdrawn",
            "You withdrew this application. You can start a new one any time.");

    private final String label;
    private final String applicantMessage;

    ApplicationStatus(String label, String applicantMessage) {
        this.label = label;
        this.applicantMessage = applicantMessage;
    }

    public String label() {
        return label;
    }

    public String applicantMessage() {
        return applicantMessage;
    }

    public boolean isEditable() {
        return this == DRAFT || this == MORE_INFO_NEEDED;
    }

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN;
    }

    public boolean isUnderReview() {
        return this == SUBMITTED || this == PROCESSING || this == VERIFIED;
    }

    public boolean acceptsDocumentReview() {
        return this == SUBMITTED || this == PROCESSING || this == MORE_INFO_NEEDED || this == VERIFIED;
    }

    public Set<ApplicationStatus> staffTargets() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(PROCESSING, MORE_INFO_NEEDED, VERIFIED, REJECTED);
            case PROCESSING -> EnumSet.of(MORE_INFO_NEEDED, VERIFIED, REJECTED);
            case MORE_INFO_NEEDED -> EnumSet.of(PROCESSING, REJECTED);
            case VERIFIED -> EnumSet.of(PROCESSING, MORE_INFO_NEEDED, REJECTED);
            default -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean staffCanMoveTo(ApplicationStatus next) {
        return staffTargets().contains(next);
    }
}