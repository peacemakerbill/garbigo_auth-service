package com.garbigo.auth.dto;

import com.garbigo.auth.model.ApplicationStatus;
import com.garbigo.auth.model.CapacityUnit;
import com.garbigo.auth.model.DocumentReviewStatus;
import com.garbigo.auth.model.DocumentType;
import com.garbigo.auth.model.ServiceType;
import com.garbigo.auth.model.VehicleType;
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

    private static final String PHONE = "^$|^(?:\\+254|254|0)[17]\\d{8}$";
    private static final String PHONE_MESSAGE = "Please enter a valid Kenyan phone number, for example 0712345678.";
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
            @Pattern(regexp = "^$|^\\d{7,9}$", message = "Your National ID number should be 7 to 9 digits.")
            String nationalIdNumber,
            @Pattern(regexp = "^$|^[AaPp]\\d{9}[A-Za-z]$", message = "Your KRA PIN should look like A123456789B.")
            String kraPin,
            @Past(message = "Your date of birth must be in the past.") LocalDate dateOfBirth,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String alternatePhone,

            @Size(max = 40, message = "The county name is too long.") String county,
            @Size(max = 80, message = "The sub-county or estate name is too long.") String subCounty,
            @Size(max = 200, message = "Your physical address is too long.") String physicalAddress,

            @Size(max = 5, message = "Please choose up to 5 services.") Set<ServiceType> serviceTypes,
            VehicleType vehicleType,
            @Pattern(regexp = "^$|^[A-Za-z0-9 ]{5,12}$", message = "Please enter the vehicle registration as it appears on the number plate, for example KDA 123A.")
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

            @Pattern(regexp = PHONE, message = "Please enter a valid M-Pesa number, for example 0712345678.") String mpesaNumber,

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
            String maskedNationalId,
            String county,
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

    public record OptionsView(
            List<String> counties,
            List<VehicleOption> vehicleTypes,
            List<Option> serviceTypes,
            List<Option> capacityUnits,
            List<Option> daysOfWeek,
            List<Option> statuses,
            UploadLimits uploadLimits,
            List<RequirementView> documents) {
    }
}