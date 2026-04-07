package ro.timetable.feedback.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.common.dto.ApiDtos.FeedbackEntryResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.feedback.service.FeedbackService;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final AuthenticatedRequestService authenticatedRequestService;
    private final FeedbackService feedbackService;

    public FeedbackController(
            AuthenticatedRequestService authenticatedRequestService,
            FeedbackService feedbackService
    ) {
        this.authenticatedRequestService = authenticatedRequestService;
        this.feedbackService = feedbackService;
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
        return feedbackService.listEntries(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }

    @GetMapping("/{feedbackId}")
    public FeedbackEntryResponse getFeedback(@PathVariable Long feedbackId, JwtAuthenticationToken authentication) {
        return feedbackService.getEntry(
                feedbackId,
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication)
        );
    }

    @PostMapping
    public FeedbackEntryResponse submitFeedback(@Valid @RequestBody SubmitFeedbackRequest request, JwtAuthenticationToken authentication) {
        return feedbackService.submitFeedback(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
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
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
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
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                request.message()
        );
    }
}
