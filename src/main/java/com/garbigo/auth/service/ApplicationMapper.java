package com.garbigo.auth.service;

import com.garbigo.auth.dto.ApplicationDtos.ApplicantAccount;
import com.garbigo.auth.dto.ApplicationDtos.ApplicationDetails;
import com.garbigo.auth.dto.ApplicationDtos.ApplicationResponse;
import com.garbigo.auth.dto.ApplicationDtos.DocumentView;
import com.garbigo.auth.dto.ApplicationDtos.EmergencyContactInput;
import com.garbigo.auth.dto.ApplicationDtos.InternalNoteView;
import com.garbigo.auth.dto.ApplicationDtos.ProgressView;
import com.garbigo.auth.dto.ApplicationDtos.ReferenceInput;
import com.garbigo.auth.dto.ApplicationDtos.RequirementView;
import com.garbigo.auth.dto.ApplicationDtos.StaffDetail;
import com.garbigo.auth.dto.ApplicationDtos.StaffSummary;
import com.garbigo.auth.dto.ApplicationDtos.StatusInfo;
import com.garbigo.auth.dto.ApplicationDtos.StatusInfoOption;
import com.garbigo.auth.dto.ApplicationDtos.TimelineEntry;
import com.garbigo.auth.model.ApplicationStatus;
import com.garbigo.auth.model.CollectorApplication;
import com.garbigo.auth.model.CollectorApplication.ApplicationDocument;
import com.garbigo.auth.model.CollectorApplication.StatusHistoryEntry;
import com.garbigo.auth.model.DocumentReviewStatus;
import com.garbigo.auth.model.DocumentType;
import com.garbigo.auth.model.ServiceType;
import com.garbigo.auth.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApplicationMapper {

    private final int maxFileMb;
    private final int reviewDays;

    public ApplicationMapper(@Value("${collector-application.max-file-mb:5}") int maxFileMb,
                             @Value("${collector-application.review-days:3}") int reviewDays) {
        this.maxFileMb = maxFileMb;
        this.reviewDays = reviewDays;
    }

    public StatusInfo statusInfo(ApplicationStatus status) {
        return new StatusInfo(status.name(), status.label(), status.applicantMessage());
    }

    public ApplicationDetails toDetails(CollectorApplication a) {
        EmergencyContactInput contact = null;
        if (a.getEmergencyContact() != null) {
            contact = new EmergencyContactInput(a.getEmergencyContact().getName(),
                    a.getEmergencyContact().getRelationship(), a.getEmergencyContact().getPhone());
        }
        List<ReferenceInput> refs = a.getReferences() == null ? List.of() : a.getReferences().stream()
                .map(r -> new ReferenceInput(r.getName(), r.getRelationship(), r.getPhone()))
                .toList();
        return new ApplicationDetails(
                a.getNationalIdNumber(), a.getKraPin(), a.getDateOfBirth(), a.getAlternatePhone(),
                a.getCounty(), a.getSubCounty(), a.getPhysicalAddress(),
                a.getServiceTypes(), a.getVehicleType(), a.getVehicleRegistration(),
                a.getCapacityValue(), a.getCapacityUnit(), a.getServiceAreas(),
                a.getMaxTravelDistanceKm(), a.getHelpersCount(),
                a.getAvailableDays(), a.getShiftStart(), a.getShiftEnd(), a.getAvailableForEmergency(),
                a.getYearsOfExperience(), a.getExperienceSummary(), a.getLanguages(), a.getMotivation(),
                a.getMpesaNumber(), contact, refs,
                a.getAcceptedTerms(), a.getConsentToBackgroundCheck(), a.getConfirmsInfoIsTrue());
    }

    public List<RequirementView> baselineRequirements() {
        return requirements(null, Collections.emptySet(), List.of());
    }

    public List<RequirementView> requirementsFor(CollectorApplication a) {
        return requirements(a.getVehicleType(), a.getServiceTypes(), a.getDocuments());
    }

    private List<RequirementView> requirements(com.garbigo.auth.model.VehicleType vehicle, Set<ServiceType> services,
                                               List<ApplicationDocument> docs) {
        return DocumentRequirementRules.forApplication(vehicle, services).stream().map(rule -> {
            DocumentType t = rule.type();
            ApplicationDocument existing = docs == null ? null
                    : docs.stream().filter(d -> d.getType() == t).findFirst().orElse(null);
            return new RequirementView(t, t.label(), t.description(), t.tip(), rule.required(), t.multiple(),
                    t.imageOnly() ? "JPG or PNG" : "PDF, JPG or PNG", maxFileMb,
                    existing != null, existing == null ? null : existing.getReviewStatus());
        }).toList();
    }

    public DocumentView toDocumentView(ApplicationDocument d, String fileUrl) {
        return new DocumentView(d.getId(), d.getType(), d.getType().label(), d.getFileName(), d.getMimeType(),
                d.getSizeBytes(), sizeLabel(d.getSizeBytes()), d.getDocumentNumber(), d.getExpiryDate(),
                d.getUploadedAt(), d.getReviewStatus(), d.getReviewStatus().label(), d.getReviewNote(),
                d.getReviewedByName(), d.getReviewedAt(), fileUrl);
    }

    private List<DocumentView> documentViews(CollectorApplication a, boolean staff) {
        if (a.getDocuments() == null) {
            return List.of();
        }
        return a.getDocuments().stream()
                .sorted(Comparator.comparing(ApplicationDocument::getUploadedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> toDocumentView(d, staff
                        ? "/collector-applications/" + a.getId() + "/documents/" + d.getId() + "/file"
                        : "/collector-applications/me/documents/" + d.getId() + "/file"))
                .toList();
    }

    private List<TimelineEntry> timeline(CollectorApplication a, boolean staff) {
        if (a.getHistory() == null) {
            return List.of();
        }
        List<TimelineEntry> list = new ArrayList<>();
        for (StatusHistoryEntry h : a.getHistory()) {
            String actor;
            if (staff) {
                actor = h.getActorName();
            } else {
                actor = "APPLICANT".equals(h.getActorType()) ? "You" : "Garbigo team";
            }
            list.add(new TimelineEntry(h.getToStatus(), h.getTitle(), h.getNote(), actor, h.getAt()));
        }
        Collections.reverse(list);
        return list;
    }

    public ApplicationResponse toApplicantResponse(CollectorApplication a) {
        ProgressView progress = ApplicationReadiness.evaluate(a);
        ApplicationStatus s = a.getStatus();
        return ApplicationResponse.builder()
                .id(a.getId())
                .referenceNumber(a.getReferenceNumber())
                .status(statusInfo(s))
                .editable(s.isEditable())
                .canSubmit(s.isEditable() && progress.readyToSubmit())
                .canWithdraw(!s.isTerminal())
                .lastNoteFromTeam(a.getLastPublicNote())
                .nextSteps(nextSteps(a, progress))
                .progress(progress)
                .applicantName(a.getApplicantName())
                .applicantEmail(a.getApplicantEmail())
                .applicantPhone(a.getApplicantPhone())
                .details(toDetails(a))
                .requirements(requirementsFor(a))
                .documents(documentViews(a, false))
                .timeline(timeline(a, false))
                .submittedAt(a.getSubmittedAt())
                .updatedAt(a.getUpdatedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private List<String> nextSteps(CollectorApplication a, ProgressView progress) {
        return switch (a.getStatus()) {
            case DRAFT -> progress.readyToSubmit()
                    ? List.of("Everything is filled in. Review your details, then submit your application.")
                    : progress.missing().stream().limit(6).toList();
            case SUBMITTED, PROCESSING -> List.of(
                    "Sit tight. We usually reply within " + reviewDays + " working days.",
                    "We will email you at every step, so keep an eye on your inbox.");
            case MORE_INFO_NEEDED -> {
                List<String> steps = new ArrayList<>();
                steps.add("Read the note from our team.");
                steps.addAll(progress.missing().stream().limit(6).toList());
                steps.add("Submit your application again when you are done.");
                yield steps;
            }
            case VERIFIED -> List.of(
                    "Your details and documents are verified.",
                    "A team member will complete your approval shortly.");
            case ACCEPTED -> List.of(
                    "Your account is now a collector account.",
                    "Open the app to start receiving collection requests.");
            case REJECTED -> List.of(
                    "Read the note from our team to see what to fix.",
                    "Start a new application when you are ready.");
            case WITHDRAWN -> List.of("Start a new application whenever you are ready.");
        };
    }

    public StaffSummary toStaffSummary(CollectorApplication a) {
        Set<DocumentType> required = DocumentRequirementRules.requiredTypes(a.getVehicleType(), a.getServiceTypes());
        List<ApplicationDocument> docs = a.getDocuments() == null ? List.of() : a.getDocuments();
        int verified = (int) docs.stream().filter(d -> d.getReviewStatus() == DocumentReviewStatus.VERIFIED).count();
        Long waiting = null;
        if (a.getSubmittedAt() != null && !a.getStatus().isTerminal() && a.getStatus() != ApplicationStatus.DRAFT) {
            waiting = Duration.between(a.getSubmittedAt(), Instant.now()).toDays();
        }
        return new StaffSummary(
                a.getId(), a.getReferenceNumber(), statusInfo(a.getStatus()),
                a.getApplicantName(), a.getApplicantEmail(), a.getApplicantPhone(),
                mask(a.getNationalIdNumber()), a.getCounty(),
                a.getVehicleType() == null ? null : a.getVehicleType().label(),
                a.getServiceTypes() == null ? List.of()
                        : a.getServiceTypes().stream().map(ServiceType::label).toList(),
                docs.size(), verified, required.size(),
                ApplicationReadiness.evaluate(a).percent(),
                a.getAssignedToName(), waiting, a.getSubmittedAt(), a.getUpdatedAt());
    }

    public StaffDetail toStaffDetail(CollectorApplication a, User account, boolean actorIsAdmin) {
        ProgressView progress = ApplicationReadiness.evaluate(a);
        List<String> blockers = verificationBlockers(a);
        List<InternalNoteView> notes = a.getInternalNotes() == null ? List.of() : a.getInternalNotes().stream()
                .map(n -> new InternalNoteView(n.getId(), n.getNote(), n.getAuthorName(), n.getAt()))
                .sorted(Comparator.comparing(InternalNoteView::at, Comparator.nullsLast(Comparator.<Instant>reverseOrder())))
                .toList();

        ApplicantAccount acc = null;
        if (account != null) {
            String fullName = java.util.stream.Stream.of(account.getFirstName(), account.getMiddleName(), account.getLastName())
                    .filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" "));
            acc = new ApplicantAccount(account.getId(), fullName, account.getEmail(), account.getPhoneNumber(),
                    account.getRole() == null ? null : account.getRole().name(),
                    account.getProfilePictureUrl(), account.isActive() && !account.isArchived());
        }

        return StaffDetail.builder()
                .id(a.getId())
                .referenceNumber(a.getReferenceNumber())
                .status(statusInfo(a.getStatus()))
                .account(acc)
                .details(toDetails(a))
                .progress(progress)
                .requirements(requirementsFor(a))
                .documents(documentViews(a, true))
                .allRequiredDocumentsVerified(blockers.isEmpty())
                .blockers(blockers)
                .allowedNextStatuses(a.getStatus().staffTargets().stream()
                        .map(s -> new StatusInfoOption(s.name(), s.label())).toList())
                .canPromote(actorIsAdmin && a.getStatus() == ApplicationStatus.VERIFIED)
                .assignedToId(a.getAssignedToId())
                .assignedToName(a.getAssignedToName())
                .timeline(timeline(a, true))
                .internalNotes(notes)
                .submissionCount(a.getSubmissionCount())
                .submittedAt(a.getSubmittedAt())
                .decidedAt(a.getDecidedAt())
                .updatedAt(a.getUpdatedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }

    public List<String> verificationBlockers(CollectorApplication a) {
        List<String> blockers = new ArrayList<>();
        List<ApplicationDocument> docs = a.getDocuments() == null ? List.of() : a.getDocuments();
        for (DocumentType type : DocumentRequirementRules.requiredTypes(a.getVehicleType(), a.getServiceTypes())) {
            ApplicationDocument doc = docs.stream().filter(d -> d.getType() == type).findFirst().orElse(null);
            if (doc == null) {
                blockers.add(type.label() + " has not been uploaded");
            } else if (doc.getReviewStatus() != DocumentReviewStatus.VERIFIED) {
                blockers.add(type.label() + " is " + doc.getReviewStatus().label().toLowerCase(Locale.ENGLISH));
            }
        }
        return blockers;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "*".repeat(value.length() - 4) + value.substring(value.length() - 2);
    }

    private static String sizeLabel(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}