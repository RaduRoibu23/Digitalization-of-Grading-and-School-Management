package ro.timetable.notifications.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.NotificationResponse;
import ro.timetable.common.dto.ApiDtos.UnreadNotificationCountResponse;
import ro.timetable.notifications.entity.NotificationEntity;
import ro.timetable.notifications.repository.NotificationRepository;
import ro.timetable.reference.entity.UserProfileEntity;
import ro.timetable.reference.entity.UserProfileSettingsEntity;
import ro.timetable.reference.repository.UserProfileRepository;

@Service
public class NotificationService {

    public record NotificationPayload(
            String title,
            String message,
            String category,
            String actionPath
    ) {
    }

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;
    private static final String DEFAULT_CATEGORY = "system";
    private static final String DEFAULT_TITLE = "Actualizare in platforma";

    private final MailService mailService;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;

    public NotificationService(
            MailService mailService,
            NotificationRepository notificationRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.mailService = mailService;
        this.notificationRepository = notificationRepository;
        this.userProfileRepository = userProfileRepository;
    }

    public List<NotificationResponse> getNotificationsForUser(String username, boolean unreadOnly, Integer limit) {
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        PageRequest pageRequest = PageRequest.of(0, safeLimit);
        List<NotificationEntity> notifications = unreadOnly
                ? notificationRepository.findByRecipientUsernameAndReadFalseOrderByCreatedAtDescIdDesc(username, pageRequest)
                : notificationRepository.findByRecipientUsernameOrderByCreatedAtDescIdDesc(username, pageRequest);

        return notifications.stream()
                .map(this::response)
                .toList();
    }

    public UnreadNotificationCountResponse getUnreadCount(String username) {
        return new UnreadNotificationCountResponse(notificationRepository.countByRecipientUsernameAndReadFalse(username));
    }

    @Transactional
    public NotificationResponse markAsRead(String username, Long notificationId) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipientUsername(notificationId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
        }
        return response(notificationRepository.save(notification));
    }

    @Transactional
    public ActionResponse markAllAsRead(String username) {
        List<NotificationEntity> unreadNotifications = notificationRepository.findByRecipientUsernameAndReadFalseOrderByCreatedAtDescIdDesc(username);
        if (unreadNotifications.isEmpty()) {
            return new ActionResponse("Nu exista notificari necitite.", null, 0);
        }

        Instant now = Instant.now();
        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(now);
        });
        notificationRepository.saveAll(unreadNotifications);
        return new ActionResponse("Toate notificarile au fost marcate ca citite.", null, unreadNotifications.size());
    }

    @Transactional
    public void createNotifications(Collection<String> usernames, String message) {
        createNotifications(usernames, new NotificationPayload(DEFAULT_TITLE, message, DEFAULT_CATEGORY, null));
    }

    @Transactional
    public void createNotifications(Collection<String> usernames, NotificationPayload payload) {
        NotificationPayload normalizedPayload = normalizePayload(payload);
        if (normalizedPayload == null) {
            return;
        }

        usernames.stream()
                .filter(username -> username != null && !username.isBlank())
                .map(String::trim)
                .distinct()
                .forEach(username -> deliverToUser(username, normalizedPayload));
    }

    @Transactional
    public NotificationResponse sendToUser(String username, String message) {
        return sendToUser(username, new NotificationPayload(DEFAULT_TITLE, message, DEFAULT_CATEGORY, null));
    }

    @Transactional
    public NotificationResponse sendToUser(String username, NotificationPayload payload) {
        NotificationPayload normalizedPayload = normalizePayload(payload);
        if (normalizedPayload == null || username == null || username.isBlank()) {
            return null;
        }
        return deliverToUser(username.trim(), normalizedPayload);
    }

    private NotificationResponse deliverToUser(String username, NotificationPayload payload) {
        UserProfileEntity profile = userProfileRepository.findByUsername(username)
                .orElse(null);
        if (profile == null) {
            return null;
        }

        NotificationResponse createdNotification = null;
        if (allowsInApp(profile.getSettings())) {
            NotificationEntity entity = notificationRepository.save(toEntity(username, payload));
            createdNotification = response(entity);
        }
        if (allowsEmail(profile.getSettings())) {
            sendNotificationEmail(profile, payload);
        }
        return createdNotification;
    }

    private NotificationPayload normalizePayload(NotificationPayload payload) {
        if (payload == null) {
            return null;
        }

        String normalizedMessage = normalizeValue(payload.message());
        if (normalizedMessage == null) {
            return null;
        }

        return new NotificationPayload(
                normalizeValue(payload.title()) == null ? DEFAULT_TITLE : normalizeValue(payload.title()),
                normalizedMessage,
                normalizeValue(payload.category()) == null ? DEFAULT_CATEGORY : normalizeValue(payload.category()).toLowerCase(),
                normalizeValue(payload.actionPath())
        );
    }

    private void sendNotificationEmail(UserProfileEntity profile, NotificationPayload payload) {
        try {
            mailService.sendNotificationEmailBestEffort(
                    profile.getEmail(),
                    fullName(profile),
                    payload.title() + ": " + payload.message()
            );
        } catch (DataAccessException ignored) {
        }
    }

    private boolean allowsEmail(UserProfileSettingsEntity settings) {
        return settings == null || settings.isEmailNotificationsEnabled();
    }

    private boolean allowsInApp(UserProfileSettingsEntity settings) {
        return settings == null || settings.isInAppNotificationsEnabled();
    }

    private NotificationEntity toEntity(String username, NotificationPayload payload) {
        NotificationEntity entity = new NotificationEntity();
        entity.setRecipientUsername(username);
        entity.setTitle(payload.title());
        entity.setCategory(payload.category());
        entity.setActionPath(payload.actionPath());
        entity.setMessage(payload.message());
        entity.setRead(false);
        entity.setReadAt(null);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private NotificationResponse response(NotificationEntity entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getRecipientUsername(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getActionPath(),
                entity.getMessage(),
                entity.isRead(),
                entity.getReadAt() == null ? null : entity.getReadAt().toString(),
                entity.getCreatedAt().toString()
        );
    }

    private String fullName(UserProfileEntity profile) {
        String fullName = ((profile.getFirstName() == null ? "" : profile.getFirstName()) + " "
                + (profile.getLastName() == null ? "" : profile.getLastName())).trim();
        return fullName.isBlank() ? profile.getUsername() : fullName;
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
