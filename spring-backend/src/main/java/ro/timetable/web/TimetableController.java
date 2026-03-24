package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import ro.timetable.model.TimetableEntry;
import ro.timetable.model.TimetableGenerationRequest;
import ro.timetable.service.AuditService;
import ro.timetable.service.SchoolDataService;
import ro.timetable.web.dto.ApiDtos.TimetableGenerationResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetables")
public class TimetableController {

    private static final List<String> APP_ROLES = List.of("student", "professor", "secretariat", "scheduler", "admin", "sysadmin");

    private final AuditService auditService;
    private final SchoolDataService schoolDataService;

    public TimetableController(AuditService auditService, SchoolDataService schoolDataService) {
        this.auditService = auditService;
        this.schoolDataService = schoolDataService;
    }

    public record UpdateTimetableEntryRequest(
            @NotNull Integer version,
            Long subject_id,
            Long room_id
    ) {
    }

    @GetMapping("/classes/{classId}")
    public List<TimetableEntry> timetableForClass(@PathVariable Long classId, JwtAuthenticationToken authentication) {
        ensureTimetableAccess(username(authentication), roles(authentication), classId);
        return schoolDataService.getTimetableForClass(classId);
    }

    @GetMapping("/me/teacher")
    public List<TimetableEntry> timetableForTeacher(JwtAuthenticationToken authentication) {
        if (!roles(authentication).contains("professor")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar profesorii pot accesa acest endpoint");
        }
        return schoolDataService.getTimetableForTeacher(username(authentication));
    }

    @PostMapping("/generate")
    public TimetableGenerationResponse generate(@Valid @RequestBody TimetableGenerationRequest request, JwtAuthenticationToken authentication) {
        if (request.class_id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "class_id is required");
        }
        ensureTimetableManagement(roles(authentication));
        TimetableGenerationResponse response = schoolDataService.generateTimetable(request.class_id());
        auditService.record(
                "Generare orar",
                username(authentication),
                "Orar generat pentru clasa " + schoolDataService.getClassById(request.class_id()).name()
        );
        return response;
    }

    @DeleteMapping("/classes/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long classId, JwtAuthenticationToken authentication) {
        ensureTimetableManagement(roles(authentication));
        String className = schoolDataService.getClassById(classId).name();
        schoolDataService.deleteTimetable(classId);
        auditService.record(
                "Stergere orar",
                username(authentication),
                "Orarul clasei " + className + " a fost sters"
        );
    }

    @PatchMapping("/entries/{entryId}")
    public TimetableEntry updateEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody UpdateTimetableEntryRequest request,
            JwtAuthenticationToken authentication
    ) {
        ensureTimetableManagement(roles(authentication));
        TimetableEntry updated = schoolDataService.updateEntry(entryId, request.version(), request.subject_id(), request.room_id());
        auditService.record(
                "Actualizare orar",
                username(authentication),
                "Orarul clasei " + updated.className() + " a fost actualizat la " + updated.subjectName() + " in sala " + updated.roomName()
        );
        return updated;
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

    private String username(JwtAuthenticationToken authentication) {
        return schoolDataService.resolveAuthenticatedUsername(
                (String) authentication.getToken().getClaims().getOrDefault("preferred_username", authentication.getName()),
                (String) authentication.getToken().getClaims().get("email")
        );
    }

    private List<String> roles(JwtAuthenticationToken authentication) {
        Object realmAccess = authentication.getToken().getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            Object roleValues = realmAccessMap.get("roles");
            if (roleValues instanceof List<?> roleList) {
                return roleList.stream()
                        .map(String::valueOf)
                        .filter(APP_ROLES::contains)
                        .toList();
            }
        }
        return List.of();
    }
}
