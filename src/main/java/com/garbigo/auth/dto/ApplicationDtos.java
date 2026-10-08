package com.garbigo.auth.dto;

import com.garbigo.auth.enums.ApplicationStatus;
import com.garbigo.auth.enums.CapacityUnit;
import com.garbigo.auth.enums.DocumentReviewStatus;
import com.garbigo.auth.enums.DocumentType;
import com.garbigo.auth.enums.IdentityDocumentType;
import com.garbigo.auth.enums.PayoutMethod;
import com.garbigo.auth.enums.ServiceType;
import com.garbigo.auth.enums.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    private static final String PHONE = "^$|^\\+?[0-9][0-9 ()\\-.]{5,19}$";
    private static final String PHONE_MESSAGE = "Please enter a valid phone number. Include your country code, for example +254 712 345 678.";
    private static final String TIME = "^$|^([01]\\d|2[0-3]):[0-5]\\d$";

    public record EmergencyContactInput(
            @Size(max = 80, message = "The emergency contact name is too long.") String name,
            @Size(max = 40, message = "The emergency contact relationship is too long.") String relationship,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone) {
    }

    public record ReferenceInput(
            @Size(max = 80, message = "A reference name is too long.") String name,
            @Size(max = 60, message = "A reference relationship is too long.") String relationship,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone) {
    }

    public record ApplicationDetails(
            IdentityDocumentType idType,
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 /\\-]{2,38}[A-Za-z0-9]$",
                    message = "Please enter your ID number exactly as it appears on the document, using letters and numbers only.")
            String idNumber,
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 /.\\-]{0,28}[A-Za-z0-9]$",
                    message = "Please enter your tax ID using letters and numbers only.")
            String taxId,
            @Past(message = "Your date of birth must be in the past.") LocalDate dateOfBirth,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String alternatePhone,

            @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "Please choose your country from the list.") String countryCode,
            @Size(max = 80, message = "The state, province or region name is too long.") String region,
            @Size(max = 80, message = "The city or town name is too long.") String city,
            @Size(max = 20, message = "The postal code is too long.") String postalCode,
            @Size(max = 200, message = "Your physical address is too long.") String physicalAddress,
            @Size(max = 50, message = "The time zone is too long.") String timeZone,

            @Size(max = 5, message = "Please choose up to 5 services.") Set<ServiceType> serviceTypes,
            VehicleType vehicleType,
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 \\-]{0,13}[A-Za-z0-9]$", message = "Please enter the vehicle registration exactly as it appears on the number plate.")
            String vehicleRegistration,
            @Positive(message = "Vehicle capacity must be more than zero.") Double capacityValue,
            CapacityUnit capacityUnit,
            @Size(max = 10, message = "Please list up to 10 areas you can serve.")
            List<@Size(max = 60, message = "An area name is too long.") String> serviceAreas,
            @Min(value = 1, message = "The distance you can travel must be at least 1 km.")
            @Max(value = 200, message = "The distance you can travel cannot be more than 200 km.")
            Integer maxTravelDistanceKm,
            @Min(value = 0, message = "The number of helpers cannot be negative.")
            @Max(value = 20, message = "The number of helpers cannot be more than 20.")
            Integer helpersCount,

            Set<DayOfWeek> availableDays,
            @Pattern(regexp = TIME, message = "Please enter your start time like 06:30.") String shiftStart,
            @Pattern(regexp = TIME, message = "Please enter your end time like 17:00.") String shiftEnd,
            Boolean availableForEmergency,

            @Min(value = 0, message = "Years of experience cannot be negative.")
            @Max(value = 60, message = "Years of experience cannot be more than 60.")
            Integer yearsOfExperience,
            @Size(max = 1000, message = "Please keep your experience summary under 1000 characters.") String experienceSummary,
            @Size(max = 8, message = "Please list up to 8 languages.")
            List<@Size(max = 30, message = "A language name is too long.") String> languages,
            @Size(max = 1000, message = "Please keep your answer under 1000 characters.") String motivation,

            PayoutMethod payoutMethod,
            @Size(max = 80, message = "The provider name is too long.") String payoutProvider,
            @Size(max = 40, message = "The account or mobile money number is too long.") String payoutAccountNumber,
            @Size(max = 80, message = "The account name is too long.") String payoutAccountName,

            @Valid EmergencyContactInput emergencyContact,
            @Size(max = 3, message = "Please add up to 3 references.") List<@Valid ReferenceInput> references,

            Boolean acceptedTerms,
            Boolean consentToBackgroundCheck,
            Boolean confirmsInfoIsTrue) {
    }

    public record StatusInfo(String code, String label, String message) {
    }

    public record Option(String code, String label) {
    }

    public record VehicleOption(String code, String label, boolean motorized) {
    }

    public record RequirementView(
            DocumentType type,
            String label,
            String description,
            String tip,
            boolean required,
            boolean multiple,
            String acceptedFormats,
            int maxFileSizeMb,
            boolean uploaded,
            DocumentReviewStatus reviewStatus) {
    }

    public record DocumentView(
            String id,
            DocumentType type,
            String typeLabel,
            String fileName,
            String mimeType,
            long sizeBytes,
            String sizeLabel,
            String documentNumber,
            LocalDate expiryDate,
            Instant uploadedAt,
            DocumentReviewStatus reviewStatus,
            String reviewStatusLabel,
            String reviewNote,
            String reviewedByName,
            Instant reviewedAt,
            String fileUrl) {
    }

    public record TimelineEntry(
            ApplicationStatus status,
            String title,
            String note,
            String actorName,
            Instant at) {
    }

    public record SectionProgress(String name, boolean done) {
    }

    public record ProgressView(
            int percent,
            boolean readyToSubmit,
            List<String> missing,
            List<SectionProgress> sections) {
    }

    @Builder
    public record ApplicationResponse(
            String id,
            String referenceNumber,
            StatusInfo status,
            boolean editable,
            boolean canSubmit,
            boolean canWithdraw,
            String lastNoteFromTeam,
            List<String> nextSteps,
            ProgressView progress,
            String applicantName,
            String applicantEmail,
            String applicantPhone,
            ApplicationDetails details,
            List<RequirementView> requirements,
            List<DocumentView> documents,
            List<TimelineEntry> timeline,
            Instant submittedAt,
            Instant updatedAt,
            Instant createdAt) {
    }

    public record MyApplicationResponse(
            boolean hasApplication,
            boolean canApply,
            String message,
            ApplicationResponse application,
            List<RequirementView> checklist) {
    }

    public record MyApplicationSummary(
            String id,
            String referenceNumber,
            StatusInfo status,
            Instant submittedAt,
            Instant createdAt) {
    }

    public record StaffSummary(
            String id,
            String referenceNumber,
            StatusInfo status,
            String applicantName,
            String applicantEmail,
            String applicantPhone,
            String maskedIdNumber,
            String countryCode,
            String country,
            String region,
            String city,
            String vehicleType,
            List<String> serviceTypes,
            int documentsUploaded,
            int documentsVerified,
            int documentsRequired,
            int completionPercent,
            String assignedToName,
            Long daysWaiting,
            Instant submittedAt,
            Instant updatedAt) {
    }

    public record ApplicantAccount(
            String userId,
            String fullName,
            String email,
            String phoneNumber,
            String role,
            String profilePictureUrl,
            boolean accountActive) {
    }

    public record InternalNoteView(String id, String note, String authorName, Instant at) {
    }

    @Builder
    public record StaffDetail(
            String id,
            String referenceNumber,
            StatusInfo status,
            ApplicantAccount account,
            ApplicationDetails details,
            ProgressView progress,
            List<RequirementView> requirements,
            List<DocumentView> documents,
            boolean allRequiredDocumentsVerified,
            List<String> blockers,
            List<StatusInfoOption> allowedNextStatuses,
            boolean canPromote,
            String assignedToId,
            String assignedToName,
            List<TimelineEntry> timeline,
            List<InternalNoteView> internalNotes,
            int submissionCount,
            Instant submittedAt,
            Instant decidedAt,
            Instant updatedAt,
            Instant createdAt) {
    }

    public record StatusInfoOption(String code, String label) {
    }

    public record StatusUpdateRequest(
            @NotNull(message = "Please choose the new status.") ApplicationStatus status,
            @Size(max = 1000, message = "Please keep the note under 1000 characters.") String note,
            @Size(max = 1000, message = "Please keep the internal note under 1000 characters.") String internalNote) {
    }

    public record DocumentReviewRequest(
            @NotNull(message = "Please choose VERIFIED or REJECTED.") DocumentReviewStatus status,
            @Size(max = 500, message = "Please keep the note under 500 characters.") String note) {
    }

    public record NoteRequest(
            @NotBlank(message = "Please write a note first.")
            @Size(max = 1000, message = "Please keep the note under 1000 characters.") String note) {
    }

    public record PromoteRequest(
            @Size(max = 1000, message = "Please keep the note under 1000 characters.") String note) {
    }

    public record WithdrawRequest(
            @Size(max = 500, message = "Please keep the reason under 500 characters.") String reason) {
    }

    public record PageResult<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record StatsView(
            long total,
            Map<String, Long> byStatus,
            long awaitingReview,
            long unassigned,
            long submittedLast7Days,
            Long oldestWaitingDays) {
    }

    public record UploadLimits(int maxFileSizeMb, String acceptedFormats) {
    }

    public record CountryOption(String code, String name, String dialCode) {
    }

    public record OptionsView(
            List<CountryOption> countries,
            List<Option> idTypes,
            List<Option> payoutMethods,
            List<VehicleOption> vehicleTypes,
            List<Option> serviceTypes,
            List<Option> capacityUnits,
            List<Option> daysOfWeek,
            List<Option> statuses,
            UploadLimits uploadLimits,
            List<RequirementView> documents) {
    }
}