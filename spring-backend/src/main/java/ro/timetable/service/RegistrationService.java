package ro.timetable.service;

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
import ro.timetable.web.dto.ApiDtos.ProfileResponse;
import ro.timetable.web.dto.ApiDtos.RegistrationResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class RegistrationService {

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

    public RegistrationService(RestTemplate restTemplate, SchoolDataService schoolDataService) {
        this.restTemplate = restTemplate;
        this.schoolDataService = schoolDataService;
    }

    public RegistrationResponse registerStudent(String username, String password, String firstName, String lastName, String email, Long classId) {
        if (schoolDataService.hasProfile(username)) {
            throw new ResponseStatusException(CONFLICT, "Username folosit deja");
        }

        String accessToken = adminAccessToken();
        createKeycloakUser(accessToken, username, password, firstName, lastName, email);
        String userId = findUserId(accessToken, username);
        assignStudentRole(accessToken, userId);

        ProfileResponse profile = schoolDataService.registerStudentProfile(username, firstName, lastName, email, classId);
        return new RegistrationResponse(
                "Account created",
                profile.id(),
                profile.username(),
                profile.role(),
                profile.first_name(),
                profile.last_name(),
                profile.email(),
                profile.class_id(),
                profile.class_name(),
                profile.class_profile(),
                profile.subjects_taught()
        );
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
                throw new ResponseStatusException(CONFLICT, "Username folosit deja");
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

    private void assignStudentRole(String accessToken, String userId) {
        HttpHeaders headers = jsonHeaders(accessToken);
        ResponseEntity<Map> roleResponse = restTemplate.exchange(
                keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/roles/student",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        Map<?, ?> role = roleResponse.getBody();
        if (role == null || role.get("id") == null || role.get("name") == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Student role could not be loaded");
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
            throw new ResponseStatusException(BAD_GATEWAY, "Could not assign student role");
        }
    }

    private HttpHeaders jsonHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}