package ro.timetable.reference.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.auth.service.AccountProvisioningService;
import ro.timetable.catalog.service.CatalogService;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.ProfileResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.reference.model.Room;
import ro.timetable.reference.model.SchoolClass;
import ro.timetable.reference.model.Subject;
import ro.timetable.reference.model.UserProfile;
import ro.timetable.reference.service.SchoolDataService;

@RestController
@RequestMapping("/api")
public class ReferenceDataController {

    private final AccountProvisioningService accountProvisioningService;
    private final AuthenticatedRequestService authenticatedRequestService;
    private final AuditService auditService;
    private final SchoolDataService schoolDataService;
    private final CatalogService catalogService;

    public ReferenceDataController(
            AccountProvisioningService accountProvisioningService,
            AuthenticatedRequestService authenticatedRequestService,
            AuditService auditService,
            SchoolDataService schoolDataService,
            CatalogService catalogService
    ) {
        this.accountProvisioningService = accountProvisioningService;
        this.authenticatedRequestService = authenticatedRequestService;
        this.auditService = auditService;
        this.schoolDataService = schoolDataService;
        this.catalogService = catalogService;
    }

    public record CreateProfileRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String role,
            @NotBlank String first_name,
            @NotBlank String last_name,
            @NotBlank @Email String email,
            Long class_id,
            List<String> subjects_taught,
            String linked_student_username
    ) {
    }

    public record CreateExternalProfessorRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String first_name,
            @NotBlank String last_name,
            @NotBlank @Email String email,
            @NotNull List<String> subjects_taught
    ) {
    }

    public record UpdateProfileRequest(
            @NotNull Integer version,
            @NotBlank String first_name,
            @NotBlank String last_name,
            @NotBlank @Email String email,
            Long class_id,
            Long homeroom_class_id,
            String address,
            String cnp,
            String series,
            String serial_number,
            String father_initial,
            String linked_student_username
    ) {
    }

    @GetMapping("/classes")
    public List<SchoolClass> classes() {
        return schoolDataService.getClasses();
    }

    @GetMapping("/subjects")
    public List<Subject> subjects() {
        return schoolDataService.getSubjects();
    }

    @GetMapping({"/rooms", "/rooms/"})
    public List<Room> rooms() {
        return schoolDataService.getRooms();
    }

    @GetMapping("/profiles")
    public List<ProfileResponse> profiles(@RequestParam(required = false) String role, JwtAuthenticationToken authentication) {
        List<String> roles = authenticatedRequestService.roles(authentication);
        if (!canManageProfiles(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar secretariatul, directorul si sysadmin-ul pot vedea lista de profiluri");
        }
        return schoolDataService.getProfilesByRole(role, canManageProfiles(roles));
    }

    @PostMapping("/profiles")
    public ProfileResponse createProfile(@Valid @RequestBody CreateProfileRequest request, JwtAuthenticationToken authentication) {
        String actorUsername = authenticatedRequestService.username(authentication);
        if (!authenticatedRequestService.roles(authentication).contains("sysadmin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar sysadmin-ul poate crea conturi noi");
        }

        ProfileResponse created = accountProvisioningService.createManagedAccount(
                request.username().trim(),
                request.password(),
                request.role().trim().toLowerCase(),
                request.first_name().trim(),
                request.last_name().trim(),
                request.email().trim(),
                request.class_id(),
                request.subjects_taught() == null ? List.of() : request.subjects_taught(),
                request.linked_student_username()
        );
        auditService.record(
                "Creare cont",
                actorUsername,
                "A fost creat contul " + created.role() + " pentru utilizatorul " + created.username()
                        + (created.class_name() == null ? "" : " in clasa " + created.class_name())
        );
        return created;
    }

    @PostMapping("/profiles/external-professors")
    public ProfileResponse createExternalProfessor(
            @Valid @RequestBody CreateExternalProfessorRequest request,
            JwtAuthenticationToken authentication
    ) {
        List<String> roles = authenticatedRequestService.roles(authentication);
        if (!(roles.contains("secretariat") || roles.contains("director") || roles.contains("sysadmin"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar secretariatul, directorul si sysadmin-ul pot crea profesori externi");
        }

        ProfileResponse created = accountProvisioningService.createManagedAccount(
                request.username().trim(),
                request.password(),
                "professor",
                request.first_name().trim(),
                request.last_name().trim(),
                request.email().trim(),
                null,
                request.subjects_taught(),
                null,
                true
        );
        auditService.record(
                "Creare profesor extern",
                authenticatedRequestService.username(authentication),
                "A fost creat profilul de profesor extern pentru utilizatorul " + created.username()
        );
        return created;
    }

    @PutMapping("/profiles/{username}")
    public ProfileResponse updateProfile(
            @PathVariable String username,
            @Valid @RequestBody UpdateProfileRequest request,
            JwtAuthenticationToken authentication
    ) {
        List<String> roles = authenticatedRequestService.roles(authentication);
        if (!canManageProfiles(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar secretariatul, directorul si sysadmin-ul pot modifica profiluri");
        }

        UserProfile previousProfile = schoolDataService.getProfile(username);
        UserProfile updatedProfile = schoolDataService.updateProfile(
                username,
                request.version(),
                request.first_name(),
                request.last_name(),
                request.email(),
                request.class_id(),
                request.address(),
                request.cnp(),
                request.series(),
                request.serial_number(),
                request.father_initial(),
                request.homeroom_class_id(),
                request.linked_student_username()
        );
        catalogService.syncProfileData(previousProfile, updatedProfile);
        accountProvisioningService.syncManagedAccountProfile(
                updatedProfile.username(),
                updatedProfile.firstName(),
                updatedProfile.lastName(),
                updatedProfile.email()
        );
        auditService.record(
                "Actualizare profil",
                authenticatedRequestService.username(authentication),
                "Profilul utilizatorului " + updatedProfile.username() + " a fost actualizat"
        );
        return schoolDataService.toProfileResponse(updatedProfile, true);
    }

    @PostMapping("/profiles/sync-identities")
    public ActionResponse syncManagedIdentities(JwtAuthenticationToken authentication) {
        if (!authenticatedRequestService.roles(authentication).contains("sysadmin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar sysadmin-ul poate sincroniza identitatile cu Keycloak");
        }

        AccountProvisioningService.IdentitySyncResult result = accountProvisioningService.syncManagedAccounts();
        auditService.record(
                "Sincronizare identitati",
                authenticatedRequestService.username(authentication),
                "Au fost sincronizate " + result.synchronizedCount() + " conturi cu Keycloak"
                        + (result.skippedCount() > 0 ? ". Au fost omise " + result.skippedCount() + " conturi fara corespondent direct." : "")
        );
        String detail = result.skippedCount() > 0
                ? "Identitatile au fost sincronizate cu Keycloak. Au fost omise " + result.skippedCount() + " conturi fara corespondent direct."
                : "Identitatile au fost sincronizate cu Keycloak.";
        return new ActionResponse(detail, null, result.synchronizedCount());
    }

    private boolean canManageProfiles(List<String> roles) {
        return roles.contains("secretariat") || roles.contains("director") || roles.contains("sysadmin");
    }
}
