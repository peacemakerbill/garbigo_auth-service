package com.garbigo.auth.service;

import com.garbigo.auth.dto.ApplicationDtos.ApplicationDetails;
import com.garbigo.auth.dto.ApplicationDtos.ApplicationResponse;
import com.garbigo.auth.dto.ApplicationDtos.CountryOption;
import com.garbigo.auth.dto.ApplicationDtos.DocumentReviewRequest;
import com.garbigo.auth.dto.ApplicationDtos.EmergencyContactInput;
import com.garbigo.auth.dto.ApplicationDtos.MyApplicationResponse;
import com.garbigo.auth.dto.ApplicationDtos.MyApplicationSummary;
import com.garbigo.auth.dto.ApplicationDtos.Option;
import com.garbigo.auth.dto.ApplicationDtos.OptionsView;
import com.garbigo.auth.dto.ApplicationDtos.PageResult;
import com.garbigo.auth.dto.ApplicationDtos.ReferenceInput;
import com.garbigo.auth.dto.ApplicationDtos.StaffDetail;
import com.garbigo.auth.dto.ApplicationDtos.StaffSummary;
import com.garbigo.auth.dto.ApplicationDtos.StatsView;
import com.garbigo.auth.dto.ApplicationDtos.StatusUpdateRequest;
import com.garbigo.auth.dto.ApplicationDtos.UploadLimits;
import com.garbigo.auth.dto.ApplicationDtos.VehicleOption;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.ApplicationStatus;
import com.garbigo.auth.model.CollectorApplication;
import com.garbigo.auth.model.CollectorApplication.ApplicationDocument;
import com.garbigo.auth.model.CollectorApplication.InternalNote;
import com.garbigo.auth.model.CollectorApplication.StatusHistoryEntry;
import com.garbigo.auth.model.DocumentReviewStatus;
import com.garbigo.auth.model.DocumentType;
import com.garbigo.auth.model.Role;
import com.garbigo.auth.model.ServiceType;
import com.garbigo.auth.model.User;
import com.garbigo.auth.model.VehicleType;
import com.garbigo.auth.model.CapacityUnit;
import com.garbigo.auth.repository.CollectorApplicationRepository;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.util.Countries;
import com.garbigo.auth.model.IdentityDocumentType;
import com.garbigo.auth.model.PayoutMethod;
import com.garbigo.auth.util.PhoneNumbers;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CollectorApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CollectorApplicationService.class);
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.OPERATIONS, Role.FINANCE, Role.SUPPORT);
    private static final int MAX_DOCUMENTS = 20;
    private static final int MAX_OTHER_DOCUMENTS = 5;
    private static final Set<String> SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "submittedAt", "referenceNumber", "status", "applicantName");

    public record SearchParams(
            String q,
            List<ApplicationStatus> statuses,
            VehicleType vehicleType,
            String country,
            String region,
            ServiceType serviceType,
            Boolean unassigned,
            String assignedTo,
            LocalDate submittedFrom,
            LocalDate submittedTo,
            String sortBy,
            String direction,
            int page,
            int size) {
    }

    public record DocumentFile(byte[] bytes, String fileName, String mimeType) {
    }

    private final CollectorApplicationRepository applications;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final GoogleDriveStorageService drive;
    private final ApplicationEmailService emails;
    private final ApplicationMapper mapper;

    @Value("${collector-application.max-file-mb:5}")
    private int maxFileMb;

    public CollectorApplicationService(CollectorApplicationRepository applications, UserRepository users,
                                       MongoTemplate mongo, GoogleDriveStorageService drive,
                                       ApplicationEmailService emails, ApplicationMapper mapper) {
        this.applications = applications;
        this.users = users;
        this.mongo = mongo;
        this.drive = drive;
        this.emails = emails;
        this.mapper = mapper;
    }

    // ============================== OPTIONS ==============================

    public OptionsView options() {
        return new OptionsView(
                Countries.all().stream().map(c -> new CountryOption(c.code(), c.name(), c.dialCode())).toList(),
                java.util.Arrays.stream(IdentityDocumentType.values()).map(t -> new Option(t.name(), t.label())).toList(),
                java.util.Arrays.stream(PayoutMethod.values()).map(m -> new Option(m.name(), m.label())).toList(),
                java.util.Arrays.stream(VehicleType.values())
                        .map(v -> new VehicleOption(v.name(), v.label(), v.motorized())).toList(),
                java.util.Arrays.stream(ServiceType.values()).map(s -> new Option(s.name(), s.label())).toList(),
                java.util.Arrays.stream(CapacityUnit.values()).map(u -> new Option(u.name(), u.label())).toList(),
                java.util.Arrays.stream(DayOfWeek.values())
                        .map(d -> new Option(d.name(), d.getDisplayName(TextStyle.FULL, Locale.ENGLISH))).toList(),
                java.util.Arrays.stream(ApplicationStatus.values()).map(s -> new Option(s.name(), s.label())).toList(),
                new UploadLimits(maxFileMb, "PDF, JPG or PNG"),
                mapper.baselineRequirements());
    }

    public List<com.garbigo.auth.dto.ApplicationDtos.RequirementView> requirements(VehicleType vehicle,
                                                                                   Set<ServiceType> services,
                                                                                   IdentityDocumentType idType) {
        CollectorApplication probe = new CollectorApplication();
        probe.setVehicleType(vehicle);
        probe.setServiceTypes(services == null ? new LinkedHashSet<>() : services);
        probe.setIdType(idType);
        return mapper.requirementsFor(probe);
    }

    // ============================== APPLICANT ==============================

    public MyApplicationResponse getMine() {
        User user = currentUser();
        Optional<CollectorApplication> latest = applications.findFirstByUserIdOrderByCreatedAtDesc(user.getId());

        if (user.getRole() == Role.COLLECTOR) {
            return new MyApplicationResponse(latest.isPresent(), false,
                    "Your account is already a collector account. You do not need to apply again.",
                    latest.map(mapper::toApplicantResponse).orElse(null), List.of());
        }
        if (user.getRole() != Role.CLIENT) {
            return new MyApplicationResponse(false, false,
                    "Collector applications are only for customer accounts.", null, List.of());
        }
        if (latest.isEmpty()) {
            return new MyApplicationResponse(false, true,
                    "Become a Garbigo collector. The application takes about 10 minutes, and you can save your progress and come back any time.",
                    null, mapper.baselineRequirements());
        }
        CollectorApplication app = latest.get();
        boolean canApply = app.getStatus().isTerminal();
        String message = switch (app.getStatus()) {
            case REJECTED -> "Your last application was not approved. Read the note from our team, then you can apply again.";
            case WITHDRAWN -> "You withdrew your last application. You can start a new one any time.";
            default -> app.getStatus().applicantMessage();
        };
        return new MyApplicationResponse(true, canApply, message, mapper.toApplicantResponse(app),
                canApply ? mapper.baselineRequirements() : List.of());
    }

    public List<MyApplicationSummary> myHistory() {
        User user = currentUser();
        return applications.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(a -> new MyApplicationSummary(a.getId(), a.getReferenceNumber(),
                        mapper.statusInfo(a.getStatus()), a.getSubmittedAt(), a.getCreatedAt()))
                .toList();
    }

    public ApplicationResponse saveMine(ApplicationDetails input) {
        User user = currentUser();
        assertCanApply(user);
        CollectorApplication app = openOrCreateDraft(user);
        assertEditable(app);
        applyDetails(app, input);
        refreshApplicant(app, user);
        applications.save(app);
        return mapper.toApplicantResponse(app);
    }

    public ApplicationResponse uploadMyDocument(DocumentType type, MultipartFile file, String documentNumber,
                                                LocalDate expiryDate) {
        User user = currentUser();
        assertCanApply(user);
        CollectorApplication app = latestOrThrow(user);
        assertEditable(app);

        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new CustomException("This document has already expired. Please upload a valid one.");
        }

        List<ApplicationDocument> docs = app.getDocuments();
        ApplicationDocument existing = type.multiple() ? null
                : docs.stream().filter(d -> d.getType() == type).findFirst().orElse(null);
        if (existing != null && existing.getReviewStatus() == DocumentReviewStatus.VERIFIED) {
            throw new CustomException("Your " + type.label()
                    + " is already verified, so there is nothing more to upload.");
        }
        if (type.multiple() && docs.stream().filter(d -> d.getType() == type).count() >= MAX_OTHER_DOCUMENTS) {
            throw new CustomException("You can add up to " + MAX_OTHER_DOCUMENTS
                    + " extra documents. Remove one before adding another.");
        }
        if (docs.size() >= MAX_DOCUMENTS && existing == null) {
            throw new CustomException("You have reached the maximum number of documents. Remove one before adding another.");
        }

        UploadedFileRules.ValidatedFile valid = UploadedFileRules.validate(file, type, maxFileMb);

        String folderId = app.getDriveFolderId();
        if (folderId == null) {
            folderId = drive.ensureFolder(app.getReferenceNumber() + " - " + safeName(app.getApplicantName()),
                    drive.rootFolderId());
            app.setDriveFolderId(folderId);
        }

        String storedName = type.name() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + valid.extension();
        GoogleDriveStorageService.StoredFile stored = drive.upload(folderId, storedName, valid.mimeType(),
                valid.bytes(), "Garbigo collector application " + app.getReferenceNumber() + " - " + type.label());

        ApplicationDocument doc = new ApplicationDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setType(type);
        doc.setDriveFileId(stored.fileId());
        doc.setFileName(valid.displayName());
        doc.setMimeType(valid.mimeType());
        doc.setSizeBytes(valid.bytes().length);
        doc.setDocumentNumber(clean(documentNumber));
        doc.setExpiryDate(expiryDate);
        doc.setUploadedAt(Instant.now());
        doc.setReviewStatus(DocumentReviewStatus.PENDING);

        String oldFileId = existing == null ? null : existing.getDriveFileId();
        if (existing != null) {
            docs.remove(existing);
        }
        docs.add(doc);
        applications.save(app);
        drive.deleteQuietly(oldFileId);
        return mapper.toApplicantResponse(app);
    }

    public ApplicationResponse deleteMyDocument(String documentId) {
        User user = currentUser();
        assertCanApply(user);
        CollectorApplication app = latestOrThrow(user);
        assertEditable(app);
        ApplicationDocument doc = findDocument(app, documentId);
        if (doc.getReviewStatus() == DocumentReviewStatus.VERIFIED) {
            throw new CustomException("This document has already been verified, so it can't be removed.");
        }
        app.getDocuments().remove(doc);
        applications.save(app);
        drive.deleteQuietly(doc.getDriveFileId());
        return mapper.toApplicantResponse(app);
    }

    public ApplicationResponse submit() {
        User user = currentUser();
        assertCanApply(user);
        CollectorApplication app = latestOrThrow(user);
        assertEditable(app);

        var progress = ApplicationReadiness.evaluate(app);
        if (!progress.readyToSubmit()) {
            throw new CustomException("Almost there. Before you submit: " + String.join("; ", progress.missing()) + ".");
        }
        if (idUsedElsewhere(app)) {
            throw new CustomException("This National ID number is already used in another application.");
        }

        refreshApplicant(app, user);
        boolean resubmission = app.getStatus() == ApplicationStatus.MORE_INFO_NEEDED;
        if (app.getSubmittedAt() == null) {
            app.setSubmittedAt(Instant.now());
        }
        app.setSubmissionCount(app.getSubmissionCount() + 1);
        app.setLastPublicNote(null);
        moveTo(app, ApplicationStatus.SUBMITTED, resubmission ? "Resubmitted with updates" : "Application submitted",
                null, user, "APPLICANT");
        applications.save(app);
        notifyApplicant(app, ApplicationStatus.SUBMITTED, null, null);
        return mapper.toApplicantResponse(app);
    }

    public ApplicationResponse withdraw(String reason) {
        User user = currentUser();
        assertCanApply(user);
        CollectorApplication app = latestOrThrow(user);
        if (app.getStatus().isTerminal()) {
            throw new CustomException("This application is already closed.");
        }
        moveTo(app, ApplicationStatus.WITHDRAWN, "Application withdrawn", clean(reason), user, "APPLICANT");
        app.setDecidedAt(Instant.now());
        applications.save(app);
        notifyApplicant(app, ApplicationStatus.WITHDRAWN, null, null);
        return mapper.toApplicantResponse(app);
    }

    public DocumentFile openMyDocument(String documentId) {
        User user = currentUser();
        CollectorApplication app = applications.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new CustomException("You don't have an application yet."));
        ApplicationDocument doc = findDocument(app, documentId);
        return new DocumentFile(drive.download(doc.getDriveFileId()), doc.getFileName(), doc.getMimeType());
    }

    // ============================== STAFF ==============================

    public PageResult<StaffSummary> search(SearchParams p) {
        User staff = requireStaff();
        List<Criteria> and = new ArrayList<>();

        if (p.statuses() == null || p.statuses().isEmpty()) {
            and.add(Criteria.where("status").ne(ApplicationStatus.DRAFT));
        } else {
            and.add(Criteria.where("status").in(p.statuses()));
        }
        if (p.q() != null && !p.q().isBlank()) {
            String rx = Pattern.quote(p.q().trim());
            and.add(new Criteria().orOperator(
                    Criteria.where("applicantName").regex(rx, "i"),
                    Criteria.where("applicantEmail").regex(rx, "i"),
                    Criteria.where("applicantPhone").regex(rx, "i"),
                    Criteria.where("referenceNumber").regex(rx, "i"),
                    Criteria.where("idNumber").regex(rx, "i"),
                    Criteria.where("taxId").regex(rx, "i")));
        }
        if (p.vehicleType() != null) {
            and.add(Criteria.where("vehicleType").is(p.vehicleType()));
        }
        if (p.country() != null && !p.country().isBlank()) {
            String code = Countries.normalize(p.country());
            and.add(Criteria.where("countryCode").is(code == null ? p.country().trim() : code));
        }
        if (p.region() != null && !p.region().isBlank()) {
            and.add(Criteria.where("region").regex("^" + Pattern.quote(p.region().trim()) + "$", "i"));
        }
        if (p.serviceType() != null) {
            and.add(Criteria.where("serviceTypes").is(p.serviceType()));
        }
        if (Boolean.TRUE.equals(p.unassigned())) {
            and.add(Criteria.where("assignedToId").is(null));
        } else if (p.assignedTo() != null && !p.assignedTo().isBlank()) {
            and.add(Criteria.where("assignedToId").is("me".equalsIgnoreCase(p.assignedTo()) ? staff.getId() : p.assignedTo()));
        }
        if (p.submittedFrom() != null || p.submittedTo() != null) {
            Criteria range = Criteria.where("submittedAt");
            if (p.submittedFrom() != null) {
                range = range.gte(p.submittedFrom().atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            if (p.submittedTo() != null) {
                range = range.lt(p.submittedTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            and.add(range);
        }

        Criteria criteria = new Criteria().andOperator(and);
        String sortField = p.sortBy() != null && SORT_FIELDS.contains(p.sortBy()) ? p.sortBy() : "updatedAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(p.direction()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int size = Math.min(Math.max(p.size(), 1), 100);
        int page = Math.max(p.page(), 0);

        long total = mongo.count(new Query(criteria), CollectorApplication.class);
        List<StaffSummary> content = mongo.find(
                        new Query(criteria).with(PageRequest.of(page, size, Sort.by(dir, sortField))),
                        CollectorApplication.class)
                .stream().map(mapper::toStaffSummary).toList();
        int totalPages = (int) Math.ceil(total / (double) size);
        return new PageResult<>(content, page, size, total, totalPages);
    }

    public StatsView stats() {
        requireStaff();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (ApplicationStatus s : ApplicationStatus.values()) {
            long count = applications.countByStatus(s);
            byStatus.put(s.name(), count);
            if (s != ApplicationStatus.DRAFT) {
                total += count;
            }
        }
        long unassigned = mongo.count(new Query(new Criteria().andOperator(
                Criteria.where("status").in(ApplicationStatus.SUBMITTED, ApplicationStatus.PROCESSING),
                Criteria.where("assignedToId").is(null))), CollectorApplication.class);
        long last7 = mongo.count(new Query(Criteria.where("submittedAt")
                .gte(Instant.now().minus(Duration.ofDays(7)))), CollectorApplication.class);
        CollectorApplication oldest = mongo.findOne(
                new Query(Criteria.where("status").is(ApplicationStatus.SUBMITTED))
                        .with(Sort.by(Sort.Direction.ASC, "submittedAt")).limit(1), CollectorApplication.class);
        Long oldestDays = oldest == null || oldest.getSubmittedAt() == null ? null
                : Duration.between(oldest.getSubmittedAt(), Instant.now()).toDays();
        return new StatsView(total, byStatus, byStatus.getOrDefault("SUBMITTED", 0L), unassigned, last7, oldestDays);
    }

    public StaffDetail getForStaff(String id) {
        User staff = requireStaff();
        return detail(find(id), staff);
    }

    public StaffDetail claim(String id) {
        User staff = requireStaff();
        CollectorApplication app = find(id);
        if (app.getStatus().isTerminal() || app.getStatus() == ApplicationStatus.DRAFT) {
            throw new CustomException("This application can't be assigned right now.");
        }
        app.setAssignedToId(staff.getId());
        app.setAssignedToName(displayName(staff));
        boolean started = app.getStatus() == ApplicationStatus.SUBMITTED;
        if (started) {
            moveTo(app, ApplicationStatus.PROCESSING, "Review started", null, staff, "STAFF");
        }
        applications.save(app);
        if (started) {
            notifyApplicant(app, ApplicationStatus.PROCESSING, null, null);
        }
        return detail(app, staff);
    }

    public StaffDetail updateStatus(String id, StatusUpdateRequest request) {
        User staff = requireStaff();
        CollectorApplication app = find(id);
        ApplicationStatus next = request.status();
        String note = clean(request.note());

        if (next == ApplicationStatus.ACCEPTED) {
            throw new CustomException("To approve an applicant, use the Make collector action. It also switches their account to a collector account.");
        }
        if (!app.getStatus().staffCanMoveTo(next)) {
            String allowed = app.getStatus().staffTargets().stream().map(ApplicationStatus::label)
                    .collect(Collectors.joining(", "));
            throw new CustomException("This application is " + app.getStatus().label().toLowerCase(Locale.ENGLISH)
                    + " and can't be moved to " + next.label().toLowerCase(Locale.ENGLISH) + "."
                    + (allowed.isEmpty() ? "" : " You can move it to: " + allowed + "."));
        }
        if ((next == ApplicationStatus.REJECTED || next == ApplicationStatus.MORE_INFO_NEEDED)
                && (note == null || note.length() < 10)) {
            throw new CustomException(next == ApplicationStatus.REJECTED
                    ? "Please tell the applicant why the application is not approved (at least a short sentence). They will see this note."
                    : "Please tell the applicant what they need to fix (at least a short sentence). They will see this note.");
        }
        if (next == ApplicationStatus.VERIFIED) {
            List<String> blockers = mapper.verificationBlockers(app);
            if (!blockers.isEmpty()) {
                throw new CustomException("Verify every required document first. Still open: "
                        + String.join("; ", blockers) + ".");
            }
            var progress = ApplicationReadiness.evaluate(app);
            if (!progress.readyToSubmit()) {
                throw new CustomException("The applicant's details are incomplete: "
                        + String.join("; ", progress.missing()) + ".");
            }
        }

        List<String> attention = null;
        if (next == ApplicationStatus.MORE_INFO_NEEDED) {
            attention = app.getDocuments().stream()
                    .filter(d -> d.getReviewStatus() == DocumentReviewStatus.REJECTED)
                    .map(d -> d.getType().label() + (d.getReviewNote() == null ? "" : ": " + d.getReviewNote()))
                    .toList();
        }

        moveTo(app, next, titleFor(next), note, staff, "STAFF");
        app.setLastPublicNote(note);
        if (next == ApplicationStatus.REJECTED) {
            app.setDecidedAt(Instant.now());
        }
        if (app.getAssignedToId() == null) {
            app.setAssignedToId(staff.getId());
            app.setAssignedToName(displayName(staff));
        }
        addInternalNote(app, request.internalNote(), staff);
        applications.save(app);
        notifyApplicant(app, next, note, attention);
        return detail(app, staff);
    }

    public StaffDetail reviewDocument(String applicationId, String documentId, DocumentReviewRequest request) {
        User staff = requireStaff();
        CollectorApplication app = find(applicationId);
        if (!app.getStatus().acceptsDocumentReview()) {
            throw new CustomException("Documents can only be reviewed once the application has been submitted and while it is open.");
        }
        ApplicationDocument doc = findDocument(app, documentId);
        String note = clean(request.note());
        if (request.status() == DocumentReviewStatus.REJECTED && (note == null || note.length() < 5)) {
            throw new CustomException("Please tell the applicant what is wrong with this document so they can replace it.");
        }

        doc.setReviewStatus(request.status());
        doc.setReviewNote(request.status() == DocumentReviewStatus.VERIFIED ? null : note);
        doc.setReviewedById(staff.getId());
        doc.setReviewedByName(displayName(staff));
        doc.setReviewedAt(Instant.now());

        boolean started = false;
        if (app.getStatus() == ApplicationStatus.SUBMITTED) {
            moveTo(app, ApplicationStatus.PROCESSING, "Review started", null, staff, "STAFF");
            if (app.getAssignedToId() == null) {
                app.setAssignedToId(staff.getId());
                app.setAssignedToName(displayName(staff));
            }
            started = true;
        }
        applications.save(app);

        if (started) {
            notifyApplicant(app, ApplicationStatus.PROCESSING, null, null);
        } else if (request.status() == DocumentReviewStatus.REJECTED
                && app.getStatus() == ApplicationStatus.MORE_INFO_NEEDED) {
            CompletableFuture.runAsync(() -> emails.sendDocumentRejected(app, doc));
        }
        return detail(app, staff);
    }

    public StaffDetail addNote(String id, String note) {
        User staff = requireStaff();
        CollectorApplication app = find(id);
        addInternalNote(app, note, staff);
        applications.save(app);
        return detail(app, staff);
    }

    public StaffDetail promote(String id, String note) {
        User staff = requireStaff();
        if (staff.getRole() != Role.ADMIN) {
            throw new CustomException("Only an administrator can make an applicant a collector.");
        }
        CollectorApplication app = find(id);
        if (app.getStatus() != ApplicationStatus.VERIFIED) {
            throw new CustomException("Verify the application first. Move it to Verified once every document has been checked, then try again.");
        }
        User applicant = users.findById(app.getUserId())
                .orElseThrow(() -> new CustomException("The applicant's account could not be found."));
        if (applicant.getRole() == Role.COLLECTOR) {
            throw new CustomException("This account is already a collector account.");
        }
        if (applicant.getRole() != Role.CLIENT) {
            throw new CustomException("Only customer accounts can be made collectors. This account's role is "
                    + applicant.getRole().name() + ".");
        }
        if (!applicant.isActive() || applicant.isArchived()) {
            throw new CustomException("This account is deactivated or archived. Reactivate it before making it a collector.");
        }

        String cleanNote = clean(note);
        applicant.setRole(Role.COLLECTOR);
        users.save(applicant);

        moveTo(app, ApplicationStatus.ACCEPTED, "Approved and made a collector", cleanNote, staff, "STAFF");
        app.setLastPublicNote(cleanNote);
        app.setDecidedAt(Instant.now());
        applications.save(app);
        notifyApplicant(app, ApplicationStatus.ACCEPTED, cleanNote, null);
        return detail(app, staff);
    }

    public DocumentFile openDocumentForStaff(String applicationId, String documentId) {
        User staff = requireStaff();
        CollectorApplication app = find(applicationId);
        ApplicationDocument doc = findDocument(app, documentId);
        log.info("APPLICATION DOCUMENT VIEWED: staff={} application={} document={} type={}",
                staff.getId(), app.getReferenceNumber(), doc.getId(), doc.getType());
        return new DocumentFile(drive.download(doc.getDriveFileId()), doc.getFileName(), doc.getMimeType());
    }

    // ============================== INTERNALS ==============================

    private StaffDetail detail(CollectorApplication app, User staff) {
        User account = users.findById(app.getUserId()).orElse(null);
        return mapper.toStaffDetail(app, account, staff.getRole() == Role.ADMIN);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new CustomException("Please sign in to continue.");
        }
        return user;
    }

    private User requireStaff() {
        User user = currentUser();
        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new CustomException("You don't have permission to do this.");
        }
        return user;
    }

    private void assertCanApply(User user) {
        if (user.getRole() == Role.COLLECTOR) {
            throw new CustomException("Your account is already a collector account. You do not need to apply again.");
        }
        if (user.getRole() != Role.CLIENT) {
            throw new CustomException("Collector applications are only for customer accounts.");
        }
    }

    private void assertEditable(CollectorApplication app) {
        if (app.getStatus().isEditable()) {
            return;
        }
        if (app.getStatus().isUnderReview()) {
            throw new CustomException("Your application is with our team, so it can't be edited right now. If we need anything, we will email you. You can also withdraw it and start again.");
        }
        throw new CustomException("This application is closed. Start a new application to make changes.");
    }

    private CollectorApplication find(String id) {
        return applications.findById(id).orElseThrow(() -> new CustomException("That application could not be found."));
    }

    private CollectorApplication latestOrThrow(User user) {
        return applications.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new CustomException("Please save your details first to start your application."));
    }

    private ApplicationDocument findDocument(CollectorApplication app, String documentId) {
        return app.getDocuments().stream().filter(d -> d.getId().equals(documentId)).findFirst()
                .orElseThrow(() -> new CustomException("That document could not be found."));
    }

    private CollectorApplication openOrCreateDraft(User user) {
        Optional<CollectorApplication> latest = applications.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent() && !latest.get().getStatus().isTerminal()) {
            return latest.get();
        }
        CollectorApplication app = new CollectorApplication();
        app.setReferenceNumber(nextReference());
        app.setUserId(user.getId());
        app.setStatus(ApplicationStatus.DRAFT);
        refreshApplicant(app, user);
        addHistory(app, null, ApplicationStatus.DRAFT, "Application started", null, user, "APPLICANT");
        return applications.save(app);
    }

    private String nextReference() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        Document counter = mongo.findAndModify(
                Query.query(Criteria.where("_id").is("collector-application-" + year)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class, "counters");
        long seq = counter == null ? 1 : ((Number) counter.get("seq")).longValue();
        return String.format(Locale.ENGLISH, "GCA-%d-%06d", year, seq);
    }

    private void refreshApplicant(CollectorApplication app, User user) {
        app.setApplicantName(displayName(user));
        app.setApplicantEmail(user.getEmail());
        app.setApplicantPhone(user.getPhoneNumber());
    }

    private void moveTo(CollectorApplication app, ApplicationStatus to, String title, String note, User actor,
                        String actorType) {
        ApplicationStatus from = app.getStatus();
        app.setStatus(to);
        app.setLastStatusChangeAt(Instant.now());
        addHistory(app, from, to, title, note, actor, actorType);
    }

    private void addHistory(CollectorApplication app, ApplicationStatus from, ApplicationStatus to, String title,
                            String note, User actor, String actorType) {
        StatusHistoryEntry entry = new StatusHistoryEntry();
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setTitle(title);
        entry.setNote(note);
        entry.setActorId(actor.getId());
        entry.setActorName(displayName(actor));
        entry.setActorType(actorType);
        entry.setAt(Instant.now());
        app.getHistory().add(entry);
    }

    private void addInternalNote(CollectorApplication app, String text, User author) {
        String cleaned = clean(text);
        if (cleaned == null) {
            return;
        }
        InternalNote note = new InternalNote();
        note.setId(UUID.randomUUID().toString());
        note.setNote(cleaned);
        note.setAuthorId(author.getId());
        note.setAuthorName(displayName(author));
        note.setAt(Instant.now());
        app.getInternalNotes().add(note);
    }

    private void notifyApplicant(CollectorApplication app, ApplicationStatus status, String note, List<String> items) {
        CompletableFuture.runAsync(() -> emails.sendStatusEmail(app, status, note, items));
    }

    private static String titleFor(ApplicationStatus status) {
        return switch (status) {
            case PROCESSING -> "Review started";
            case MORE_INFO_NEEDED -> "More information requested";
            case VERIFIED -> "Details and documents verified";
            case REJECTED -> "Application not approved";
            default -> status.label();
        };
    }

    private static String displayName(User user) {
        String full = java.util.stream.Stream.of(user.getFirstName(), user.getMiddleName(), user.getLastName())
                .filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" "));
        return full.isBlank() ? user.getEmail() : full;
    }

    private static String safeName(String name) {
        if (name == null) {
            return "Applicant";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9 ]", "").trim();
        return cleaned.isEmpty() ? "Applicant" : cleaned;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String phone(String raw, String label, String countryCode) {
        String cleaned = clean(raw);
        if (cleaned == null) {
            return null;
        }
        String normalized = PhoneNumbers.normalize(cleaned, countryCode);
        if (normalized == null) {
            throw new CustomException("Please enter a valid phone number for your " + label
                    + ". Start with your country code, for example +254712345678"
                    + (countryCode == null ? ", or choose your country first." : "."));
        }
        return normalized;
    }

    private List<String> cleanList(List<String> values) {
        return values.stream().map(CollectorApplicationService::clean).filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toCollection(ArrayList::new));
    }

    private void applyDetails(CollectorApplication app, ApplicationDetails in) {
        if (in.countryCode() != null) {
            String raw = clean(in.countryCode());
            if (raw == null) {
                app.setCountryCode(null);
            } else {
                String code = Countries.normalize(raw);
                if (code == null) {
                    throw new CustomException("We could not recognise that country. Please choose one from the list.");
                }
                app.setCountryCode(code);
            }
        }
        if (in.idType() != null) {
            app.setIdType(in.idType());
        }
        if (in.idNumber() != null) {
            String id = clean(in.idNumber());
            app.setIdNumber(id == null ? null : id.toUpperCase(Locale.ENGLISH).replaceAll("\\s+", " "));
            if (app.getIdNumber() != null && idUsedElsewhere(app)) {
                app.setIdNumber(null);
                throw new CustomException("This ID number is already used in another application. If you think this is a mistake, please contact support.");
            }
        }
        if (in.taxId() != null) {
            String tax = clean(in.taxId());
            app.setTaxId(tax == null ? null : tax.toUpperCase(Locale.ENGLISH));
        }
        if (in.dateOfBirth() != null) {
            int age = Period.between(in.dateOfBirth(), LocalDate.now()).getYears();
            if (age < 18) {
                throw new CustomException("You must be at least 18 years old to apply.");
            }
            if (age > 80) {
                throw new CustomException("Please check your date of birth. It looks too far in the past.");
            }
            app.setDateOfBirth(in.dateOfBirth());
        }
        if (in.alternatePhone() != null) {
            app.setAlternatePhone(phone(in.alternatePhone(), "alternate phone number", app.getCountryCode()));
        }

        if (in.region() != null) {
            app.setRegion(clean(in.region()));
        }
        if (in.city() != null) {
            app.setCity(clean(in.city()));
        }
        if (in.postalCode() != null) {
            app.setPostalCode(clean(in.postalCode()));
        }
        if (in.physicalAddress() != null) {
            app.setPhysicalAddress(clean(in.physicalAddress()));
        }
        if (in.timeZone() != null) {
            String zone = clean(in.timeZone());
            if (zone != null && !java.time.ZoneId.getAvailableZoneIds().contains(zone)) {
                throw new CustomException("We could not recognise that time zone. Please choose one from the list, for example Africa/Nairobi.");
            }
            app.setTimeZone(zone);
        }

        if (in.serviceTypes() != null) {
            app.setServiceTypes(new LinkedHashSet<>(in.serviceTypes()));
        }
        if (in.vehicleType() != null) {
            app.setVehicleType(in.vehicleType());
            if (!in.vehicleType().motorized()) {
                app.setVehicleRegistration(null);
            }
        }
        if (in.vehicleRegistration() != null) {
            String reg = clean(in.vehicleRegistration());
            app.setVehicleRegistration(reg == null ? null : reg.toUpperCase(Locale.ENGLISH).replaceAll("\\s+", " "));
        }
        if (in.capacityValue() != null) {
            app.setCapacityValue(in.capacityValue());
        }
        if (in.capacityUnit() != null) {
            app.setCapacityUnit(in.capacityUnit());
        }
        if (in.serviceAreas() != null) {
            app.setServiceAreas(cleanList(in.serviceAreas()));
        }
        if (in.maxTravelDistanceKm() != null) {
            app.setMaxTravelDistanceKm(in.maxTravelDistanceKm());
        }
        if (in.helpersCount() != null) {
            app.setHelpersCount(in.helpersCount());
        }

        if (in.availableDays() != null) {
            app.setAvailableDays(new LinkedHashSet<>(in.availableDays()));
        }
        if (in.shiftStart() != null) {
            app.setShiftStart(clean(in.shiftStart()));
        }
        if (in.shiftEnd() != null) {
            app.setShiftEnd(clean(in.shiftEnd()));
        }
        if (in.availableForEmergency() != null) {
            app.setAvailableForEmergency(in.availableForEmergency());
        }

        if (in.yearsOfExperience() != null) {
            app.setYearsOfExperience(in.yearsOfExperience());
        }
        if (in.experienceSummary() != null) {
            app.setExperienceSummary(clean(in.experienceSummary()));
        }
        if (in.languages() != null) {
            app.setLanguages(cleanList(in.languages()));
        }
        if (in.motivation() != null) {
            app.setMotivation(clean(in.motivation()));
        }
        if (in.payoutMethod() != null) {
            app.setPayoutMethod(in.payoutMethod());
        }
        if (in.payoutProvider() != null) {
            app.setPayoutProvider(clean(in.payoutProvider()));
        }
        if (in.payoutAccountNumber() != null) {
            String account = clean(in.payoutAccountNumber());
            if (account != null && app.getPayoutMethod() == PayoutMethod.MOBILE_MONEY) {
                account = phone(account, "mobile money number", app.getCountryCode());
            }
            app.setPayoutAccountNumber(account);
        }
        if (in.payoutAccountName() != null) {
            app.setPayoutAccountName(clean(in.payoutAccountName()));
        }

        EmergencyContactInput ec = in.emergencyContact();
        if (ec != null) {
            CollectorApplication.EmergencyContact contact = app.getEmergencyContact() == null
                    ? new CollectorApplication.EmergencyContact() : app.getEmergencyContact();
            if (ec.name() != null) {
                contact.setName(clean(ec.name()));
            }
            if (ec.relationship() != null) {
                contact.setRelationship(clean(ec.relationship()));
            }
            if (ec.phone() != null) {
                contact.setPhone(phone(ec.phone(), "emergency contact", app.getCountryCode()));
            }
            app.setEmergencyContact(contact);
        }
        if (in.references() != null) {
            List<CollectorApplication.Reference> refs = new ArrayList<>();
            for (ReferenceInput r : in.references()) {
                if (r == null || clean(r.name()) == null) {
                    continue;
                }
                CollectorApplication.Reference ref = new CollectorApplication.Reference();
                ref.setName(clean(r.name()));
                ref.setRelationship(clean(r.relationship()));
                ref.setPhone(phone(r.phone(), "reference", app.getCountryCode()));
                refs.add(ref);
            }
            app.setReferences(refs);
        }

        if (in.acceptedTerms() != null) {
            app.setAcceptedTerms(in.acceptedTerms());
        }
        if (in.consentToBackgroundCheck() != null) {
            app.setConsentToBackgroundCheck(in.consentToBackgroundCheck());
        }
        if (in.confirmsInfoIsTrue() != null) {
            app.setConfirmsInfoIsTrue(in.confirmsInfoIsTrue());
        }
    }

    private boolean idUsedElsewhere(CollectorApplication app) {
        if (app.getIdNumber() == null) {
            return false;
        }
        Criteria c = new Criteria().andOperator(
                Criteria.where("idNumber").is(app.getIdNumber()),
                Criteria.where("idType").is(app.getIdType()),
                Criteria.where("countryCode").is(app.getCountryCode()),
                Criteria.where("userId").ne(app.getUserId()),
                Criteria.where("status").in(ApplicationStatus.SUBMITTED, ApplicationStatus.PROCESSING,
                        ApplicationStatus.MORE_INFO_NEEDED, ApplicationStatus.VERIFIED, ApplicationStatus.ACCEPTED));
        return mongo.exists(new Query(c), CollectorApplication.class);
    }
}