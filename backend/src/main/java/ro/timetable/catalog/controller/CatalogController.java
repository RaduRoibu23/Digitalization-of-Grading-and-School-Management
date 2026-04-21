package ro.timetable.catalog.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.catalog.service.CatalogService;
import ro.timetable.common.dto.ApiDtos.AbsenceResponse;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.CatalogResponse;
import ro.timetable.common.dto.ApiDtos.ClassSummaryResponse;
import ro.timetable.common.dto.ApiDtos.GradeChangeRequestResponse;
import ro.timetable.common.dto.ApiDtos.GradeResponse;
import ro.timetable.common.dto.ApiDtos.ProfileResponse;
import ro.timetable.common.security.AuthenticatedRequestService;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final AuthenticatedRequestService authenticatedRequestService;
    private final CatalogService catalogService;

    public CatalogController(
            AuthenticatedRequestService authenticatedRequestService,
            CatalogService catalogService
    ) {
        this.authenticatedRequestService = authenticatedRequestService;
        this.catalogService = catalogService;
    }

    public record CreateGradeRequest(
            @NotBlank String student_username,
            @NotBlank String subject_name,
            @NotNull @Min(value = 1, message = "Nota invalida") @Max(value = 10, message = "Nota invalida") Integer grade_value,
            @NotBlank String grade_date,
            @Size(max = 1000, message = "Comentariul poate avea maximum 1000 de caractere") String comment
    ) {
    }

    public record UpdateGradeRequest(
            @NotNull Integer version,
            @NotNull @Min(value = 1, message = "Nota invalida") @Max(value = 10, message = "Nota invalida") Integer grade_value,
            @NotBlank String grade_date,
            @Size(max = 1000, message = "Comentariul poate avea maximum 1000 de caractere") String comment
    ) {
    }

    public record CreateGradeChangeRequest(
            @NotBlank String request_type,
            @Min(value = 1, message = "Nota invalida") @Max(value = 10, message = "Nota invalida") Integer proposed_grade_value,
            String proposed_grade_date,
            @Size(max = 1000, message = "Comentariul poate avea maximum 1000 de caractere") String proposed_comment,
            @NotBlank @Size(max = 255, message = "Motivul poate avea maximum 255 de caractere") String reason
    ) {
    }

    public record ReviewGradeChangeRequest(
            @Size(max = 255, message = "Nota de rezolvare poate avea maximum 255 de caractere") String resolution_note
    ) {
    }

    public record CreateAbsenceRequest(
            @NotBlank String student_username,
            @NotBlank String subject_name,
            @NotBlank String absence_date
    ) {
    }

    public record MotivateAbsenceRequest(
            @NotNull Integer version,
            @Size(max = 1000, message = "Motivul poate avea maximum 1000 de caractere") String reason
    ) {
    }

    @GetMapping("/students")
    public List<ProfileResponse> catalogStudents(JwtAuthenticationToken authentication) {
        return catalogService.getCatalogStudents(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }

    @GetMapping("/me")
    public CatalogResponse myCatalog(JwtAuthenticationToken authentication) {
        return catalogService.getMyCatalog(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }

    @GetMapping("/students/{studentUsername}")
    public CatalogResponse studentCatalog(@PathVariable String studentUsername, JwtAuthenticationToken authentication) {
        return catalogService.getCatalogForStudent(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                studentUsername
        );
    }

    @PostMapping("/grades")
    public GradeResponse createGrade(@Valid @RequestBody CreateGradeRequest request, JwtAuthenticationToken authentication) {
        return catalogService.createGrade(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                request.student_username(),
                request.subject_name(),
                request.grade_value(),
                request.grade_date(),
                request.comment()
        );
    }

    @PatchMapping("/grades/{gradeId}")
    public GradeResponse updateGrade(
            @PathVariable Long gradeId,
            @Valid @RequestBody UpdateGradeRequest request,
            JwtAuthenticationToken authentication
    ) {
        return catalogService.updateGrade(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                gradeId,
                request.version(),
                request.grade_value(),
                request.grade_date(),
                request.comment()
        );
    }

    @DeleteMapping("/grades/{gradeId}")
    public ActionResponse deleteGrade(@PathVariable Long gradeId, JwtAuthenticationToken authentication) {
        return catalogService.deleteGrade(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                gradeId
        );
    }

    @PostMapping("/grades/{gradeId}/change-requests")
    public GradeChangeRequestResponse createGradeChangeRequest(
            @PathVariable Long gradeId,
            @Valid @RequestBody CreateGradeChangeRequest request,
            JwtAuthenticationToken authentication
    ) {
        return catalogService.createGradeChangeRequest(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                gradeId,
                request.request_type(),
                request.proposed_grade_value(),
                request.proposed_grade_date(),
                request.proposed_comment(),
                request.reason()
        );
    }

    @GetMapping("/grade-change-requests")
    public List<GradeChangeRequestResponse> gradeChangeRequests(
            JwtAuthenticationToken authentication,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status
    ) {
        return catalogService.getGradeChangeRequests(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                status
        );
    }

    @PatchMapping("/grade-change-requests/{requestId}/approve")
    public GradeChangeRequestResponse approveGradeChangeRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) ReviewGradeChangeRequest request,
            JwtAuthenticationToken authentication
    ) {
        return catalogService.approveGradeChangeRequest(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                requestId,
                request == null ? null : request.resolution_note()
        );
    }

    @PatchMapping("/grade-change-requests/{requestId}/reject")
    public GradeChangeRequestResponse rejectGradeChangeRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) ReviewGradeChangeRequest request,
            JwtAuthenticationToken authentication
    ) {
        return catalogService.rejectGradeChangeRequest(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                requestId,
                request == null ? null : request.resolution_note()
        );
    }

    @PostMapping("/absences")
    public AbsenceResponse createAbsence(@Valid @RequestBody CreateAbsenceRequest request, JwtAuthenticationToken authentication) {
        return catalogService.createAbsence(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                request.student_username(),
                request.subject_name(),
                request.absence_date()
        );
    }

    @PatchMapping("/absences/{absenceId}/motivate")
    public AbsenceResponse motivateAbsence(
            @PathVariable Long absenceId,
            @Valid @RequestBody MotivateAbsenceRequest request,
            JwtAuthenticationToken authentication
    ) {
        return catalogService.motivateAbsence(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                absenceId,
                request.version(),
                request.reason()
        );
    }

    @GetMapping("/exportable-classes")
    public List<ClassSummaryResponse> exportableClasses(JwtAuthenticationToken authentication) {
        return catalogService.getExportableClasses(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }

    @GetMapping("/classes/{classId}/export")
    public ResponseEntity<byte[]> exportClassCatalog(@PathVariable Long classId, JwtAuthenticationToken authentication) {
        byte[] payload = catalogService.exportClassCatalog(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                classId
        );
        String filename = "catalog-" + classId + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(payload);
    }
}
