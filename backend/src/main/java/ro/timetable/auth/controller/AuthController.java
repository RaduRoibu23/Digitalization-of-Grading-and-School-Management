package ro.timetable.auth.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import ro.timetable.audit.service.AuditService;
import ro.timetable.auth.service.AccountProvisioningService;
import ro.timetable.common.dto.ApiDtos.ApiErrorResponse;
import ro.timetable.common.dto.ApiDtos.HealthResponse;
import ro.timetable.common.dto.ApiDtos.LoginResponse;
import ro.timetable.common.dto.ApiDtos.MeResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.reference.service.SchoolDataService;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AccountProvisioningService accountProvisioningService;
    private final AuditService auditService;
    private final AuthenticatedRequestService authenticatedRequestService;
    private final RestTemplate restTemplate;
    private final SchoolDataService schoolDataService;

    public AuthController(
            AccountProvisioningService accountProvisioningService,
            AuditService auditService,
            AuthenticatedRequestService authenticatedRequestService,
            RestTemplate restTemplate,
            SchoolDataService schoolDataService
    ) {
        this.accountProvisioningService = accountProvisioningService;
        this.auditService = auditService;
        this.authenticatedRequestService = authenticatedRequestService;
        this.restTemplate = restTemplate;
        this.schoolDataService = schoolDataService;
    }

    @Value("${keycloak.token-url}")
    private String keycloakTokenUrl;

    @Value("${keycloak.client-id}")
    private String keycloakClientId;

    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank(message = "refreshToken is required") String refreshToken
    ) {
    }

    public record UpdateMyProfileSettingsRequest(
            boolean email_notifications_enabled,
            boolean in_app_notifications_enabled
    ) {
    }

    public record UpdateMyProfileRequest(
            @NotNull Integer version,
            @NotBlank String first_name,
            @NotBlank String last_name,
            @NotBlank @Email String email,
            String address,
            @NotNull UpdateMyProfileSettingsRequest settings
    ) {
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("OK");
    }

    @GetMapping("/me")
    public MeResponse me(JwtAuthenticationToken authentication) {
        return schoolDataService.meResponse(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                authentication.getToken().getClaims()
        );
    }

    @PutMapping("/me/profile")
    public MeResponse updateMyProfile(
            @Valid @RequestBody UpdateMyProfileRequest request,
            JwtAuthenticationToken authentication
    ) {
        String username = authenticatedRequestService.username(authentication);
        List<String> roles = authenticatedRequestService.roles(authentication);
        Map<String, Object> claims = authentication.getToken().getClaims();
        MeResponse updated = schoolDataService.updateMyProfile(
                username,
                roles,
                claims,
                request.version(),
                request.first_name(),
                request.last_name(),
                request.email(),
                request.address(),
                request.settings().email_notifications_enabled(),
                request.settings().in_app_notifications_enabled()
        );
        accountProvisioningService.syncManagedAccountProfile(
                updated.username(),
                updated.first_name(),
                updated.last_name(),
                updated.email()
        );
        auditService.record(
                "Actualizare profil propriu",
                username,
                "Utilizatorul si-a actualizat datele personale si preferintele de notificare"
        );
        return updated;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", keycloakClientId);
        form.add("username", request.username());
        form.add("password", request.password());

        return exchangeToken(form);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", keycloakClientId);
        form.add("refresh_token", request.refreshToken());
        return exchangeToken(form);
    }

    private ResponseEntity<?> exchangeToken(MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(keycloakTokenUrl, entity, Map.class);
            if (response.getBody() == null || response.getBody().isEmpty()) {
                return ResponseEntity.internalServerError().body(new ApiErrorResponse(
                        "login_failed",
                        "Keycloak returned an empty response body",
                        null,
                        null,
                        null
                ));
            }

            return ResponseEntity.status(response.getStatusCode()).body(toLoginResponse(response.getBody()));
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(new ApiErrorResponse(
                    "login_failed",
                    "Autentificarea a esuat",
                    ex.getStatusCode().value(),
                    null,
                    null
            ));
        } catch (ResourceAccessException ex) {
            return ResponseEntity.internalServerError().body(new ApiErrorResponse(
                    "keycloak_unreachable",
                    "Backend could not reach Keycloak",
                    null,
                    null,
                    null
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(new ApiErrorResponse(
                    "login_failed",
                    "Autentificarea nu a putut fi procesata",
                    null,
                    null,
                    null
            ));
        }
    }

    private LoginResponse toLoginResponse(Map<?, ?> body) {
        return new LoginResponse(
                asString(body.get("access_token")),
                asInteger(body.get("expires_in")),
                asInteger(body.get("refresh_expires_in")),
                asString(body.get("refresh_token")),
                asString(body.get("token_type")),
                asString(body.get("id_token")),
                asInteger(body.get("not-before-policy")),
                asString(body.get("session_state")),
                asString(body.get("scope"))
        );
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
