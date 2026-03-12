package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.service.CatalogService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private static final List<String> APP_ROLES = List.of("student", "professor", "secretariat", "scheduler", "admin", "sysadmin");

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public record CreateGradeRequest(
            @NotBlank String student_username,
            @NotBlank String subject_name,
            @NotNull @Min(value = 1, message = "Nota invalida") @Max(value = 10, message = "Nota invalida") Integer grade_value,
            @NotBlank String grade_date
    ) {
    }

    public record UpdateGradeRequest(
            @NotNull Integer version,
            @NotNull @Min(value = 1, message = "Nota invalida") @Max(value = 10, message = "Nota invalida") Integer grade_value,
            @NotBlank String grade_date
    ) {
    }

    @GetMapping("/students")
    public List<Map<String, Object>> catalogStudents(JwtAuthenticationToken authentication) {
        return catalogService.getCatalogStudents(username(authentication), roles(authentication));
    }

    @GetMapping("/me")
    public Map<String, Object> myCatalog(JwtAuthenticationToken authentication) {
        return catalogService.getMyCatalog(username(authentication), roles(authentication));
    }

    @GetMapping("/students/{studentUsername}")
    public Map<String, Object> studentCatalog(@PathVariable String studentUsername, JwtAuthenticationToken authentication) {
        return catalogService.getCatalogForStudent(username(authentication), roles(authentication), studentUsername);
    }

    @PostMapping("/grades")
    public Map<String, Object> createGrade(@Valid @RequestBody CreateGradeRequest request, JwtAuthenticationToken authentication) {
        return catalogService.createGrade(
                username(authentication),
                roles(authentication),
                request.student_username(),
                request.subject_name(),
                request.grade_value(),
                request.grade_date()
        );
    }

    @PatchMapping("/grades/{gradeId}")
    public Map<String, Object> updateGrade(
            @PathVariable Long gradeId,
            @Valid @RequestBody UpdateGradeRequest request,
            JwtAuthenticationToken authentication
    ) {
        return catalogService.updateGrade(
                username(authentication),
                roles(authentication),
                gradeId,
                request.version(),
                request.grade_value(),
                request.grade_date()
        );
    }

    @DeleteMapping("/grades/{gradeId}")
    public Map<String, Object> deleteGrade(@PathVariable Long gradeId, JwtAuthenticationToken authentication) {
        return catalogService.deleteGrade(username(authentication), roles(authentication), gradeId);
    }

    private String username(JwtAuthenticationToken authentication) {
        return (String) authentication.getToken().getClaims().getOrDefault("preferred_username", authentication.getName());
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
