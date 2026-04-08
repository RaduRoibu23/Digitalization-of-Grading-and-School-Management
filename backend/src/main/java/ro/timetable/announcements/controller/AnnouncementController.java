package ro.timetable.announcements.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.announcements.service.AnnouncementService;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.AnnouncementResponse;
import ro.timetable.common.security.AuthenticatedRequestService;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final AuthenticatedRequestService authenticatedRequestService;

    public AnnouncementController(
            AnnouncementService announcementService,
            AuthenticatedRequestService authenticatedRequestService
    ) {
        this.announcementService = announcementService;
        this.authenticatedRequestService = authenticatedRequestService;
    }

    public record CreateAnnouncementRequest(
            @Size(max = 160) String title,
            @Size(max = 1200) String message
    ) {
    }

    @GetMapping
    public List<AnnouncementResponse> listAnnouncements(
            @RequestParam(required = false) Integer limit,
            JwtAuthenticationToken authentication
    ) {
        return announcementService.listAnnouncements(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                limit
        );
    }

    @PostMapping
    public AnnouncementResponse createAnnouncement(
            @Valid @RequestBody CreateAnnouncementRequest request,
            JwtAuthenticationToken authentication
    ) {
        return announcementService.createAnnouncement(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                request.title(),
                request.message()
        );
    }

    @DeleteMapping("/{announcementId}")
    public ActionResponse deleteAnnouncement(
            @PathVariable Long announcementId,
            JwtAuthenticationToken authentication
    ) {
        return announcementService.deleteAnnouncement(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                announcementId
        );
    }
}
