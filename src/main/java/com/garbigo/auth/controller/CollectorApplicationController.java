package com.garbigo.auth.controller;

import com.garbigo.auth.dto.ApplicationDtos.ApplicationDetails;
import com.garbigo.auth.dto.ApplicationDtos.ApplicationResponse;
import com.garbigo.auth.dto.ApplicationDtos.DocumentReviewRequest;
import com.garbigo.auth.dto.ApplicationDtos.MyApplicationResponse;
import com.garbigo.auth.dto.ApplicationDtos.MyApplicationSummary;
import com.garbigo.auth.dto.ApplicationDtos.NoteRequest;
import com.garbigo.auth.dto.ApplicationDtos.OptionsView;
import com.garbigo.auth.dto.ApplicationDtos.PageResult;
import com.garbigo.auth.dto.ApplicationDtos.PromoteRequest;
import com.garbigo.auth.dto.ApplicationDtos.RequirementView;
import com.garbigo.auth.dto.ApplicationDtos.StaffDetail;
import com.garbigo.auth.dto.ApplicationDtos.StaffSummary;
import com.garbigo.auth.dto.ApplicationDtos.StatsView;
import com.garbigo.auth.dto.ApplicationDtos.StatusUpdateRequest;
import com.garbigo.auth.dto.ApplicationDtos.WithdrawRequest;
import com.garbigo.auth.model.ApplicationStatus;
import com.garbigo.auth.model.DocumentType;
import com.garbigo.auth.model.ServiceType;
import com.garbigo.auth.model.VehicleType;
import com.garbigo.auth.service.CollectorApplicationService;
import com.garbigo.auth.service.CollectorApplicationService.DocumentFile;
import com.garbigo.auth.service.CollectorApplicationService.SearchParams;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/collector-applications")
public class CollectorApplicationController {

    private final CollectorApplicationService service;

    public CollectorApplicationController(CollectorApplicationService service) {
        this.service = service;
    }

    // ====================== FOR EVERYONE SIGNED IN ======================

    @GetMapping("/options")
    public ResponseEntity<OptionsView> options() {
        return ResponseEntity.ok(service.options());
    }

    @GetMapping("/requirements")
    public ResponseEntity<List<RequirementView>> requirements(
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) Set<ServiceType> serviceTypes) {
        return ResponseEntity.ok(service.requirements(vehicleType, serviceTypes));
    }

    // ====================== APPLICANT ======================

    @GetMapping("/me")
    public ResponseEntity<MyApplicationResponse> myApplication() {
        return ResponseEntity.ok(service.getMine());
    }

    @GetMapping("/me/history")
    public ResponseEntity<List<MyApplicationSummary>> myHistory() {
        return ResponseEntity.ok(service.myHistory());
    }

    @PutMapping("/me")
    public ResponseEntity<ApplicationResponse> saveMyDetails(@Valid @RequestBody ApplicationDetails details) {
        return ResponseEntity.ok(service.saveMine(details));
    }

    @PostMapping(path = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationResponse> uploadMyDocument(
            @RequestParam("type") DocumentType type,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @RequestParam(value = "expiryDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
        return ResponseEntity.ok(service.uploadMyDocument(type, file, documentNumber, expiryDate));
    }

    @DeleteMapping("/me/documents/{documentId}")
    public ResponseEntity<ApplicationResponse> deleteMyDocument(@PathVariable String documentId) {
        return ResponseEntity.ok(service.deleteMyDocument(documentId));
    }

    @GetMapping("/me/documents/{documentId}/file")
    public ResponseEntity<byte[]> myDocumentFile(@PathVariable String documentId) {
        return file(service.openMyDocument(documentId));
    }

    @PostMapping("/me/submit")
    public ResponseEntity<ApplicationResponse> submit() {
        return ResponseEntity.ok(service.submit());
    }

    @PostMapping("/me/withdraw")
    public ResponseEntity<ApplicationResponse> withdraw(@Valid @RequestBody(required = false) WithdrawRequest request) {
        return ResponseEntity.ok(service.withdraw(request == null ? null : request.reason()));
    }

    // ====================== STAFF: ADMIN, OPERATIONS, FINANCE, SUPPORT ======================

    @GetMapping
    public ResponseEntity<PageResult<StaffSummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) String county,
            @RequestParam(required = false) ServiceType serviceType,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.search(new SearchParams(q, status, vehicleType, county, serviceType,
                unassigned, assignedTo, submittedFrom, submittedTo, sortBy, direction, page, size)));
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsView> stats() {
        return ResponseEntity.ok(service.stats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffDetail> get(@PathVariable String id) {
        return ResponseEntity.ok(service.getForStaff(id));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<StaffDetail> claim(@PathVariable String id) {
        return ResponseEntity.ok(service.claim(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<StaffDetail> updateStatus(@PathVariable String id,
                                                    @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request));
    }

    @PatchMapping("/{id}/documents/{documentId}/review")
    public ResponseEntity<StaffDetail> reviewDocument(@PathVariable String id, @PathVariable String documentId,
                                                      @Valid @RequestBody DocumentReviewRequest request) {
        return ResponseEntity.ok(service.reviewDocument(id, documentId, request));
    }

    @GetMapping("/{id}/documents/{documentId}/file")
    public ResponseEntity<byte[]> documentFile(@PathVariable String id, @PathVariable String documentId) {
        return file(service.openDocumentForStaff(id, documentId));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<StaffDetail> addNote(@PathVariable String id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(service.addNote(id, request.note()));
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<StaffDetail> promote(@PathVariable String id,
                                               @Valid @RequestBody(required = false) PromoteRequest request) {
        return ResponseEntity.ok(service.promote(id, request == null ? null : request.note()));
    }

    private ResponseEntity<byte[]> file(DocumentFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }
}