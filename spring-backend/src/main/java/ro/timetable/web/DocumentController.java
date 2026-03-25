package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.service.DocumentService;
import ro.timetable.service.SchoolDataService;
import ro.timetable.web.dto.ApiDtos.DocumentRequestResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final List<String> APP_ROLES = List.of("student", "professor", "secretariat", "scheduler", "admin", "sysadmin");

    private final DocumentService documentService;
    private final SchoolDataService schoolDataService;

    public DocumentController(DocumentService documentService, SchoolDataService schoolDataService) {
        this.documentService = documentService;
        this.schoolDataService = schoolDataService;
    }

    public record CreateDocumentRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 20) String purpose
    ) {
    }

    public record RejectDocumentRequest(
            @NotBlank @Size(max = 255) String reason
    ) {
    }

    @GetMapping("/requests")
    public List<DocumentRequestResponse> listRequests(JwtAuthenticationToken authentication) {
        return documentService.listRequests(username(authentication), roles(authentication));
    }

    @PostMapping("/requests")
    public DocumentRequestResponse createRequest(@Valid @RequestBody CreateDocumentRequest request, JwtAuthenticationToken authentication) {
        return documentService.createStudentRequest(
                username(authentication),
                roles(authentication),
                request.type(),
                request.purpose()
        );
    }

    @PatchMapping("/requests/{requestId}/approve")
    public DocumentRequestResponse approve(@PathVariable Long requestId, JwtAuthenticationToken authentication) {
        return documentService.approveRequest(requestId, username(authentication), roles(authentication));
    }

    @PatchMapping("/requests/{requestId}/reject")
    public DocumentRequestResponse reject(
            @PathVariable Long requestId,
            @Valid @RequestBody RejectDocumentRequest request,
            JwtAuthenticationToken authentication
    ) {
        return documentService.rejectRequest(requestId, username(authentication), roles(authentication), request.reason());
    }

    @GetMapping("/requests/{requestId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long requestId, JwtAuthenticationToken authentication) {
        return documentService.downloadApprovedDocument(requestId, username(authentication), roles(authentication));
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
