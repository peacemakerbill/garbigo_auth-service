package com.garbigo.auth.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@Document(collection = "collector_applications")
public class CollectorApplication {

    @Id
    private String id;

    @Indexed(unique = true)
    private String referenceNumber;

    @Indexed
    private String userId;

    private String applicantName;
    private String applicantEmail;
    private String applicantPhone;

    @Indexed
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    private IdentityDocumentType idType;
    private String idNumber;
    private String taxId;
    private LocalDate dateOfBirth;
    private String alternatePhone;

    private String countryCode;
    private String region;
    private String city;
    private String postalCode;
    private String physicalAddress;
    private String timeZone;

    private Set<ServiceType> serviceTypes = new LinkedHashSet<>();
    private VehicleType vehicleType;
    private String vehicleRegistration;
    private Double capacityValue;
    private CapacityUnit capacityUnit;
    private List<String> serviceAreas = new ArrayList<>();
    private Integer maxTravelDistanceKm;
    private Integer helpersCount;

    private Set<DayOfWeek> availableDays = new LinkedHashSet<>();
    private String shiftStart;
    private String shiftEnd;
    private Boolean availableForEmergency;

    private Integer yearsOfExperience;
    private String experienceSummary;
    private List<String> languages = new ArrayList<>();
    private String motivation;

    private PayoutMethod payoutMethod;
    private String payoutProvider;
    private String payoutAccountNumber;
    private String payoutAccountName;

    private EmergencyContact emergencyContact;
    private List<Reference> references = new ArrayList<>();

    private Boolean acceptedTerms;
    private Boolean consentToBackgroundCheck;
    private Boolean confirmsInfoIsTrue;

    private String driveFolderId;
    private List<ApplicationDocument> documents = new ArrayList<>();

    private List<StatusHistoryEntry> history = new ArrayList<>();
    private List<InternalNote> internalNotes = new ArrayList<>();

    private String assignedToId;
    private String assignedToName;

    private String lastPublicNote;
    private int submissionCount;
    private Instant submittedAt;
    private Instant lastStatusChangeAt;
    private Instant decidedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    public static class EmergencyContact {
        private String name;
        private String relationship;
        private String phone;
    }

    @Data
    public static class Reference {
        private String name;
        private String relationship;
        private String phone;
    }

    @Data
    public static class ApplicationDocument {
        private String id;
        private DocumentType type;
        private String driveFileId;
        private String fileName;
        private String mimeType;
        private long sizeBytes;
        private String documentNumber;
        private LocalDate expiryDate;
        private Instant uploadedAt;
        private DocumentReviewStatus reviewStatus = DocumentReviewStatus.PENDING;
        private String reviewNote;
        private String reviewedById;
        private String reviewedByName;
        private Instant reviewedAt;
    }

    @Data
    public static class StatusHistoryEntry {
        private ApplicationStatus fromStatus;
        private ApplicationStatus toStatus;
        private String title;
        private String note;
        private String actorId;
        private String actorName;
        private String actorType;
        private Instant at;
    }

    @Data
    public static class InternalNote {
        private String id;
        private String note;
        private String authorId;
        private String authorName;
        private Instant at;
    }
}