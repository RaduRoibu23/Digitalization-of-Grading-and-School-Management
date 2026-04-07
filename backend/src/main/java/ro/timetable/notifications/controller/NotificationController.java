package ro.timetable.notifications.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.MailStatusResponse;
import ro.timetable.common.dto.ApiDtos.NotificationDispatchResponse;
import ro.timetable.common.dto.ApiDtos.NotificationResponse;
import ro.timetable.common.dto.ApiDtos.UnreadNotificationCountResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.notifications.service.MailService;
import ro.timetable.notifications.service.NotificationService;
import ro.timetable.notifications.service.NotificationService.NotificationPayload;
import ro.timetable.reference.model.UserProfile;
import ro.timetable.reference.service.SchoolDataService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final AuditService auditService;
    private final AuthenticatedRequestService authenticatedRequestService;
    private final MailService mailService;
    private final NotificationService notificationService;
    private final SchoolDataService schoolDataService;

    public NotificationController(
            AuditService auditService,
            AuthenticatedRequestService authenticatedRequestService,
            MailService mailService,
            NotificationService notificationService,
            SchoolDataService schoolDataService
    ) {
        this.auditService = auditService;
        this.authenticatedRequestService = authenticatedRequestService;
        this.mailService = mailService;
        this.notificationService = notificationService;
        this.schoolDataService = schoolDataService;
    }

    public record SendNotificationRequest(
            @NotBlank String target_type,
            Long target_id,
            String target_username,
            String title,
            String category,
            String action_path,
            @NotBlank String message
    ) {
    }

    public record TestEmailRequest(
            String target_username,
            @Email String target_email,
            String message
    ) {
    }

    @GetMapping("/me")
    public List<NotificationResponse> myNotifications(
            @RequestParam(name = "unread_only", defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "limit", required = false) Integer limit,
            JwtAuthenticationToken authentication
    ) {
        return notificationService.getNotificationsForUser(authenticatedRequestService.username(authentication), unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(JwtAuthenticationToken authentication) {
        return notificationService.getUnreadCount(authenticatedRequestService.username(authentication));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable Long notificationId, JwtAuthenticationToken authentication) {
        return notificationService.markAsRead(authenticatedRequestService.username(authentication), notificationId);
    }

    @PatchMapping("/read-all")
    public ActionResponse markAllRead(JwtAuthenticationToken authentication) {
        return notificationService.markAllAsRead(authenticatedRequestService.username(authentication));
    }

    @GetMapping("/mail-status")
    public MailStatusResponse mailStatus(JwtAuthenticationToken authentication) {
        if (!canManageMail(authenticatedRequestService.roles(authentication))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar adminul si sysadminul pot verifica statusul emailului");
        }
        return mailService.getStatus();
    }

    @PostMapping("/test-email")
    public ActionResponse sendTestEmail(@Valid @RequestBody TestEmailRequest request, JwtAuthenticationToken authentication) {
        List<String> roles = authenticatedRequestService.roles(authentication);
        if (!canManageMail(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar adminul si sysadminul pot trimite emailuri de test");
        }

        String requesterUsername = authenticatedRequestService.username(authentication);
        UserProfile recipient = resolveTestRecipient(request, requesterUsername);
        mailService.sendTestEmailOrThrow(
                request.target_email() == null || request.target_email().isBlank() ? recipient.email() : request.target_email().trim(),
                recipient.firstName() + " " + recipient.lastName(),
                request.message()
        );
        auditService.record(
                "Email de test",
                requesterUsername,
                "A fost trimis un email de test catre " + (request.target_email() == null || request.target_email().isBlank() ? recipient.email() : request.target_email().trim())
        );
        return new ActionResponse("Emailul de test a fost trimis.", null, null);
    }

    @PostMapping("/send")
    public NotificationDispatchResponse sendNotification(@Valid @RequestBody SendNotificationRequest request, JwtAuthenticationToken authentication) {
        List<String> roles = authenticatedRequestService.roles(authentication);
        if (!(roles.contains("professor") || roles.contains("secretariat") || roles.contains("admin") || roles.contains("sysadmin"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to send notifications");
        }

        String targetType = request.target_type().trim().toLowerCase();
        if ("user".equals(targetType)) {
            if (request.target_username() == null || request.target_username().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target_username is required for user notifications");
            }
            schoolDataService.getProfile(request.target_username().trim());
            NotificationResponse notification = notificationService.sendToUser(
                    request.target_username().trim(),
                    new NotificationPayload(request.title(), request.message(), request.category(), request.action_path())
            );
            auditService.record(
                    "Trimitere notificare",
                    authenticatedRequestService.username(authentication),
                    "A fost trimisa o notificare catre utilizatorul " + request.target_username().trim()
            );
            return new NotificationDispatchResponse("Notification sent", null, notification);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sunt permise doar notificarile trimise catre un singur utilizator");
    }

    private UserProfile resolveTestRecipient(TestEmailRequest request, String requesterUsername) {
        if (request.target_username() != null && !request.target_username().isBlank()) {
            return schoolDataService.getProfile(request.target_username().trim());
        }
        return schoolDataService.getProfile(requesterUsername);
    }

    private boolean canManageMail(List<String> roles) {
        return roles.contains("admin") || roles.contains("sysadmin");
    }
}
