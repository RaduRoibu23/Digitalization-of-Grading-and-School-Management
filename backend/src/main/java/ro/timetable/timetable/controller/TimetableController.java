package ro.timetable.timetable.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.TimetableGenerationResponse;
import ro.timetable.common.dto.ApiDtos.TimetableMoveOptionsResponse;
import ro.timetable.common.dto.ApiDtos.TimetableMoveResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.reference.model.UserProfile;
import ro.timetable.reference.service.SchoolDataService;
import ro.timetable.timetable.model.TimetableEntry;
import ro.timetable.timetable.model.TimetableGenerationRequest;
import ro.timetable.timetable.service.TimetablePdfService;

@RestController
@RequestMapping("/api/timetables")
public class TimetableController {

    private final AuditService auditService;
    private final AuthenticatedRequestService authenticatedRequestService;
    private final SchoolDataService schoolDataService;
    private final TimetablePdfService timetablePdfService;

    public TimetableController(
            AuditService auditService,
            AuthenticatedRequestService authenticatedRequestService,
            SchoolDataService schoolDataService,
            TimetablePdfService timetablePdfService
    ) {
        this.auditService = auditService;
        this.authenticatedRequestService = authenticatedRequestService;
        this.schoolDataService = schoolDataService;
        this.timetablePdfService = timetablePdfService;
    }

    public record UpdateTimetableEntryRequest(
            @NotNull Integer version,
            Long subject_id,
            Long room_id
    ) {
    }

    public record TimetableMoveOptionsRequest(@NotNull Integer entry_version) {
    }

    public record TimetableMoveRequest(
            @NotNull Integer entry_version,
            @NotNull Integer target_weekday,
            @NotNull Integer target_index_in_day,
            @NotBlank String mode
    ) {
    }

    @GetMapping("/classes/{classId}")
    public List<TimetableEntry> timetableForClass(@PathVariable Long classId, JwtAuthenticationToken authentication) {
        ensureTimetableAccess(authenticatedRequestService.username(authentication), authenticatedRequestService.roles(authentication), classId);
        return schoolDataService.getTimetableForClass(classId);
    }

    @GetMapping("/me/teacher")
    public List<TimetableEntry> timetableForTeacher(JwtAuthenticationToken authentication) {
        if (!authenticatedRequestService.roles(authentication).contains("professor")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar profesorii pot accesa acest endpoint");
        }
        return schoolDataService.getTimetableForTeacher(authenticatedRequestService.username(authentication));
    }

    @GetMapping("/classes/{classId}/download")
    public ResponseEntity<byte[]> downloadClassTimetable(@PathVariable Long classId, JwtAuthenticationToken authentication) {
        String actorUsername = authenticatedRequestService.username(authentication);
        List<String> roles = authenticatedRequestService.roles(authentication);
        ensureTimetableAccess(actorUsername, roles, classId);
        String className = schoolDataService.getClassById(classId).name();
        auditService.record(
                "Descarcare PDF orar",
                actorUsername,
                "PDF generat pentru orarul clasei " + className
        );
        return timetablePdfService.renderClassTimetablePdf(className, schoolDataService.getTimetableForClass(classId));
    }

    @GetMapping("/me/download")
    public ResponseEntity<byte[]> downloadMyTimetable(JwtAuthenticationToken authentication) {
        String actorUsername = authenticatedRequestService.username(authentication);
        List<String> roles = authenticatedRequestService.roles(authentication);

        if (roles.contains("professor")) {
            UserProfile profile = schoolDataService.getProfile(actorUsername);
            String teacherName = profile.firstName() + " " + profile.lastName();
            auditService.record(
                    "Descarcare PDF orar propriu",
                    actorUsername,
                    "PDF generat pentru orarul profesorului " + teacherName
            );
            return timetablePdfService.renderTeacherTimetablePdf(
                    teacherName,
                    schoolDataService.getTimetableForTeacher(actorUsername)
            );
        }

        if (roles.contains("student")) {
            UserProfile profile = schoolDataService.getProfile(actorUsername);
            if (profile.classId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nu pot determina clasa elevului");
            }
            String className = schoolDataService.getClassById(profile.classId()).name();
            auditService.record(
                    "Descarcare PDF orar propriu",
                    actorUsername,
                    "PDF generat pentru orarul elevului din clasa " + className
            );
            return timetablePdfService.renderClassTimetablePdf(className, schoolDataService.getTimetableForClass(profile.classId()));
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nu ai acces la acest export");
    }

    @PostMapping("/generate")
    public TimetableGenerationResponse generate(@Valid @RequestBody TimetableGenerationRequest request, JwtAuthenticationToken authentication) {
        if (request.class_id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "class_id is required");
        }
        ensureTimetableManagement(authenticatedRequestService.roles(authentication));
        TimetableGenerationResponse response = schoolDataService.generateTimetable(request.class_id());
        auditService.record(
                "Generare orar",
                authenticatedRequestService.username(authentication),
                "Orar generat pentru clasa " + schoolDataService.getClassById(request.class_id()).name()
        );
        return response;
    }

    @DeleteMapping("/classes/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long classId, JwtAuthenticationToken authentication) {
        ensureTimetableManagement(authenticatedRequestService.roles(authentication));
        String className = schoolDataService.getClassById(classId).name();
        schoolDataService.deleteTimetable(classId);
        auditService.record(
                "Stergere orar",
                authenticatedRequestService.username(authentication),
                "Orarul clasei " + className + " a fost sters"
        );
    }

    @PatchMapping("/entries/{entryId}")
    public TimetableEntry updateEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody UpdateTimetableEntryRequest request,
            JwtAuthenticationToken authentication
    ) {
        ensureTimetableManagement(authenticatedRequestService.roles(authentication));
        TimetableEntry updated = schoolDataService.updateEntry(entryId, request.version(), request.subject_id(), request.room_id());
        auditService.record(
                "Actualizare orar",
                authenticatedRequestService.username(authentication),
                "Orarul clasei " + updated.className() + " a fost actualizat la " + updated.subjectName() + " in sala " + updated.roomName()
        );
        return updated;
    }

    @PostMapping("/entries/{entryId}/move-options")
    public TimetableMoveOptionsResponse moveOptions(
            @PathVariable Long entryId,
            @Valid @RequestBody TimetableMoveOptionsRequest request,
            JwtAuthenticationToken authentication
    ) {
        ensureTimetableManagement(authenticatedRequestService.roles(authentication));
        return schoolDataService.moveOptions(entryId, request.entry_version());
    }

    @PostMapping("/entries/{entryId}/move")
    public TimetableMoveResponse moveEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody TimetableMoveRequest request,
            JwtAuthenticationToken authentication
    ) {
        ensureTimetableManagement(authenticatedRequestService.roles(authentication));
        TimetableMoveResponse response = schoolDataService.moveEntry(
                entryId,
                request.entry_version(),
                request.target_weekday(),
                request.target_index_in_day(),
                request.mode()
        );
        auditService.record(
                "Mutare manuala orar",
                authenticatedRequestService.username(authentication),
                "O ora a fost mutata manual in consola de administrare pentru intrarea " + entryId
        );
        return response;
    }

    private void ensureTimetableAccess(String username, List<String> roles, Long classId) {
        if (!schoolDataService.canAccessTimetableForClass(username, roles, classId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nu poti accesa orarul pentru aceasta clasa");
        }
    }

    private void ensureTimetableManagement(List<String> roles) {
        if (!schoolDataService.canManageTimetables(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nu ai dreptul sa modifici orarul");
        }
    }
}
