package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import ro.timetable.service.NotificationService;
import ro.timetable.service.SchoolDataService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final List<String> APP_ROLES = List.of("student", "professor", "secretariat", "scheduler", "admin", "sysadmin");

    private final NotificationService notificationService;
    private final SchoolDataService schoolDataService;

    public NotificationController(NotificationService notificationService, SchoolDataService schoolDataService) {
        this.notificationService = notificationService;
        this.schoolDataService = schoolDataService;
    }

    public record SendNotificationRequest(
            @NotBlank String target_type,
            Long target_id,
            String target_username,
            @NotBlank String message
    ) {
    }

    @GetMapping("/me")
    public List<Map<String, Object>> myNotifications(
            @RequestParam(name = "unread_only", defaultValue = "false") boolean unreadOnly,
            JwtAuthenticationToken authentication
    ) {
        return notificationService.getNotificationsForUser(username(authentication), unreadOnly);
    }

    @PatchMapping("/{notificationId}/read")
    public Map<String, Object> markRead(@PathVariable Long notificationId, JwtAuthenticationToken authentication) {
        return notificationService.markAsRead(username(authentication), notificationId);
    }

    @PostMapping("/send")
    public Map<String, Object> sendNotification(@Valid @RequestBody SendNotificationRequest request, JwtAuthenticationToken authentication) {
        List<String> roles = roles(authentication);
        if (!(roles.contains("professor") || roles.contains("secretariat") || roles.contains("admin") || roles.contains("sysadmin"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to send notifications");
        }

        String targetType = request.target_type().trim().toLowerCase();
        if ("class".equals(targetType)) {
            if (request.target_id() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target_id is required for class notifications");
            }
            List<String> recipients = schoolDataService.getStudentUsernamesForClass(request.target_id());
            notificationService.createNotifications(recipients, request.message());
            return Map.of("detail", "Notifications sent", "recipients", recipients.size());
        }

        if ("user".equals(targetType)) {
            if (request.target_username() == null || request.target_username().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target_username is required for user notifications");
            }
            schoolDataService.getProfile(request.target_username());
            return notificationService.sendToUser(request.target_username(), request.message());
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported notification target");
    }

    private String username(JwtAuthenticationToken authentication) {
        return (String) authentication.getToken().getClaims().getOrDefault("preferred_username", authentication.getName());
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
