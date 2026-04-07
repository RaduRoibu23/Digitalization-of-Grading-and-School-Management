package ro.timetable.documents.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.common.dto.ApiDtos.DocumentRequestResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.documents.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final AuthenticatedRequestService authenticatedRequestService;
    private final DocumentService documentService;

    public DocumentController(
            AuthenticatedRequestService authenticatedRequestService,
            DocumentService documentService
    ) {
        this.authenticatedRequestService = authenticatedRequestService;
        this.documentService = documentService;
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
        return documentService.listRequests(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }

    @PostMapping("/requests")
    public DocumentRequestResponse createRequest(@Valid @RequestBody CreateDocumentRequest request, JwtAuthenticationToken authentication) {
        return documentService.createStudentRequest(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                request.type(),
                request.purpose()
        );
    }

    @PatchMapping("/requests/{requestId}/approve")
    public DocumentRequestResponse approve(@PathVariable Long requestId, JwtAuthenticationToken authentication) {
        return documentService.approveRequest(
                requestId,
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }

    @PatchMapping("/requests/{requestId}/reject")
    public DocumentRequestResponse reject(
            @PathVariable Long requestId,
            @Valid @RequestBody RejectDocumentRequest request,
            JwtAuthenticationToken authentication
    ) {
        return documentService.rejectRequest(
                requestId,
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                request.reason()
        );
    }

    @GetMapping("/requests/{requestId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long requestId, JwtAuthenticationToken authentication) {
        return documentService.downloadApprovedDocument(
                requestId,
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }
}
