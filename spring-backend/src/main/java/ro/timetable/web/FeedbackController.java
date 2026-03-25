package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.service.FeedbackService;
import ro.timetable.service.SchoolDataService;
import ro.timetable.web.dto.ApiDtos.FeedbackEntryResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final List<String> APP_ROLES = List.of("student", "professor", "secretariat", "scheduler", "admin", "sysadmin");

    private final FeedbackService feedbackService;
    private final SchoolDataService schoolDataService;

    public FeedbackController(FeedbackService feedbackService, SchoolDataService schoolDataService) {
        this.feedbackService = feedbackService;
        this.schoolDataService = schoolDataService;
    }

    public record SubmitFeedbackRequest(
            @NotBlank String category,
            @NotBlank String satisfaction,
            boolean wants_contact,
            @NotBlank @Size(max = 2000) String message
    ) {
    }

    public record UpdateFeedbackStatusRequest(
            @NotBlank String status
    ) {
    }

    public record ReplyToFeedbackRequest(
            @NotBlank @Size(max = 2000) String message
    ) {
    }

    @GetMapping
    public List<FeedbackEntryResponse> listFeedback(JwtAuthenticationToken authentication) {
        return feedbackService.listEntries(username(authentication), roles(authentication));
    }

    @GetMapping("/{feedbackId}")
    public FeedbackEntryResponse getFeedback(@PathVariable Long feedbackId, JwtAuthenticationToken authentication) {
        return feedbackService.getEntry(feedbackId, username(authentication), roles(authentication));
    }

    @PostMapping
    public FeedbackEntryResponse submitFeedback(@Valid @RequestBody SubmitFeedbackRequest request, JwtAuthenticationToken authentication) {
        return feedbackService.submitFeedback(
                username(authentication),
                roles(authentication),
                request.category(),
                request.satisfaction(),
                request.wants_contact(),
                request.message()
        );
    }

    @PatchMapping("/{feedbackId}/status")
    public FeedbackEntryResponse updateStatus(
            @PathVariable Long feedbackId,
            @Valid @RequestBody UpdateFeedbackStatusRequest request,
            JwtAuthenticationToken authentication
    ) {
        return feedbackService.updateStatus(
                feedbackId,
                username(authentication),
                roles(authentication),
                request.status()
        );
    }

    @PatchMapping("/{feedbackId}/reply")
    public FeedbackEntryResponse reply(
            @PathVariable Long feedbackId,
            @Valid @RequestBody ReplyToFeedbackRequest request,
            JwtAuthenticationToken authentication
    ) {
        return feedbackService.replyToFeedback(
                feedbackId,
                username(authentication),
                roles(authentication),
                request.message()
        );
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
