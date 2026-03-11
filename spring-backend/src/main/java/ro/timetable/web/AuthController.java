package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import ro.timetable.service.SchoolDataService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final List<String> APP_ROLES = List.of("student", "professor", "secretariat", "scheduler", "admin", "sysadmin");

    private final RestTemplate restTemplate;
    private final SchoolDataService schoolDataService;

    public AuthController(RestTemplate restTemplate, SchoolDataService schoolDataService) {
        this.restTemplate = restTemplate;
        this.schoolDataService = schoolDataService;
    }

    @Value("${keycloak.token-url}")
    private String keycloakTokenUrl;

    @Value("${keycloak.client-id}")
    private String keycloakClientId;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "OK");
    }

    @GetMapping("/me")
    public Map<String, Object> me(JwtAuthenticationToken authentication) {
        List<String> roles = List.of();
        Object realmAccess = authentication.getToken().getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            Object roleValues = realmAccessMap.get("roles");
            if (roleValues instanceof List<?> roleList) {
                roles = roleList.stream()
                        .map(String::valueOf)
                        .filter(APP_ROLES::contains)
                        .toList();
            }
        }

        String username = (String) authentication.getToken().getClaims().getOrDefault("preferred_username", authentication.getName());
        return schoolDataService.meResponse(username, roles, authentication.getToken().getClaims());
    }

    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password
    ) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", keycloakClientId);
        form.add("username", request.username());
        form.add("password", request.password());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(keycloakTokenUrl, entity, Map.class);
            if (response.getBody() == null || response.getBody().isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "login_failed",
                        "detail", "Keycloak returned an empty response body"
                ));
            }

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            Map<String, Object> body = Map.of(
                    "error", "login_failed",
                    "status", ex.getStatusCode().value(),
                    "detail", ex.getResponseBodyAsString()
            );
            return ResponseEntity.status(ex.getStatusCode()).body(body);
        } catch (ResourceAccessException ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "keycloak_unreachable",
                    "detail", "Backend could not reach Keycloak",
                    "message", ex.getMostSpecificCause().getMessage()
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "login_failed",
                    "detail", ex.getMessage()
            ));
        }
    }
}

