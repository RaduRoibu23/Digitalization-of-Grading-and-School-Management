package ro.timetable.auth.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import ro.timetable.common.dto.ApiDtos.ProfileResponse;
import ro.timetable.reference.service.SchoolDataService;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class AccountProvisioningService {

    private final RestTemplate restTemplate;
    private final SchoolDataService schoolDataService;

    @Value("${keycloak.base-url}")
    private String keycloakBaseUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Value("${keycloak.admin.username}")
    private String keycloakAdminUsername;

    @Value("${keycloak.admin.password}")
    private String keycloakAdminPassword;

    public AccountProvisioningService(RestTemplate restTemplate, SchoolDataService schoolDataService) {
        this.restTemplate = restTemplate;
        this.schoolDataService = schoolDataService;
    }

    public ProfileResponse createManagedAccount(
            String username,
            String password,
            String role,
            String firstName,
            String lastName,
            String email,
            Long classId,
            List<String> subjectsTaught
    ) {
        String normalizedUsername = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        String accessToken = adminAccessToken();
        createKeycloakUser(accessToken, normalizedUsername, password, firstName, lastName, email);
        String userId = findUserId(accessToken, normalizedUsername);

        try {
            assignRealmRole(accessToken, userId, role);
            return schoolDataService.createManagedProfile(normalizedUsername, role, firstName, lastName, email, classId, subjectsTaught);
        } catch (RuntimeException exception) {
            deleteKeycloakUser(accessToken, userId);
            throw exception;
        }
    }

    private String adminAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "admin-cli");
        form.add("grant_type", "password");
        form.add("username", keycloakAdminUsername);
        form.add("password", keycloakAdminPassword);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    keycloakBaseUrl + "/realms/master/protocol/openid-connect/token",
                    new HttpEntity<>(form, headers),
                    Map.class
            );
            Object token = response.getBody() == null ? null : response.getBody().get("access_token");
            if (token == null) {
                throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Could not obtain admin token");
            }
            return String.valueOf(token);
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Keycloak admin authentication failed");
        }
    }

    private void createKeycloakUser(String accessToken, String username, String password, String firstName, String lastName, String email) {
        HttpHeaders headers = jsonHeaders(accessToken);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("enabled", true);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("email", email);
        body.put("credentials", List.of(Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        )));

        try {
            restTemplate.exchange(
                    keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Void.class
            );
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new ResponseStatusException(CONFLICT, "Username sau email folosit deja");
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Could not create Keycloak user");
        }
    }

    private String findUserId(String accessToken, String username) {
        HttpHeaders headers = jsonHeaders(accessToken);
        String url = UriComponentsBuilder
                .fromHttpUrl(keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users")
                .queryParam("username", username)
                .queryParam("exact", true)
                .toUriString();

        ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
        List<?> users = response.getBody();
        if (users == null || users.isEmpty() || !(users.get(0) instanceof Map<?, ?> user)) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Created user could not be loaded");
        }
        Object id = user.get("id");
        if (id == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Created user has no id");
        }
        return String.valueOf(id);
    }

    private void assignRealmRole(String accessToken, String userId, String roleName) {
        HttpHeaders headers = jsonHeaders(accessToken);
        ResponseEntity<Map> roleResponse = restTemplate.exchange(
                keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/roles/" + roleName,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        Map<?, ?> role = roleResponse.getBody();
        if (role == null || role.get("id") == null || role.get("name") == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Role could not be loaded");
        }

        List<Map<String, Object>> payload = List.of(Map.of(
                "id", String.valueOf(role.get("id")),
                "name", String.valueOf(role.get("name"))
        ));

        try {
            restTemplate.exchange(
                    keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId + "/role-mappings/realm",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Void.class
            );
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Could not assign role");
        }
    }

    private void deleteKeycloakUser(String accessToken, String userId) {
        try {
            restTemplate.exchange(
                    keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(jsonHeaders(accessToken)),
                    Void.class
            );
        } catch (Exception ignored) {
        }
    }

    private HttpHeaders jsonHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
