package ro.timetable.notifications.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.common.dto.ApiDtos.NotificationResponse;
import ro.timetable.notifications.entity.NotificationEntity;
import ro.timetable.notifications.repository.NotificationRepository;
import ro.timetable.reference.entity.UserProfileEntity;
import ro.timetable.reference.repository.UserProfileRepository;

@Service
public class NotificationService {

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

    public List<NotificationResponse> getNotificationsForUser(String username, boolean unreadOnly) {
        List<NotificationEntity> notifications = unreadOnly
                ? notificationRepository.findByRecipientUsernameAndReadFalseOrderByCreatedAtDescIdDesc(username)
                : notificationRepository.findByRecipientUsernameOrderByCreatedAtDescIdDesc(username);

        return notifications.stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(String username, Long notificationId) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipientUsername(notificationId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return response(notificationRepository.save(notification));
    }

    @Transactional
    public void createNotifications(Collection<String> usernames, String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        List<String> recipients = usernames.stream()
                .filter(username -> username != null && !username.isBlank())
                .distinct()
                .toList();

        List<NotificationEntity> entities = recipients.stream()
                .map(username -> toEntity(username, message))
                .toList();

        if (!entities.isEmpty()) {
            notificationRepository.saveAll(entities);
            recipients.forEach(username -> sendNotificationEmail(username, message));
        }
    }

    @Transactional
    public NotificationResponse sendToUser(String username, String message) {
        NotificationEntity entity = notificationRepository.save(toEntity(username, message));
        sendNotificationEmail(username, message);
        return response(entity);
    }

    private void sendNotificationEmail(String username, String message) {
        try {
            userProfileRepository.findByUsername(username)
                    .ifPresent(profile -> {
                        // #onetoone Email-ul respecta setarile profilului incarcate prin relatia one-to-one profile -> settings.
                        boolean emailEnabled = profile.getSettings() == null || profile.getSettings().isEmailNotificationsEnabled();
                        if (emailEnabled) {
                            mailService.sendNotificationEmailBestEffort(
                                    profile.getEmail(),
                                    fullName(profile),
                                    message
                            );
                        }
                    });
        } catch (DataAccessException ignored) {
        }
    }

    private NotificationEntity toEntity(String username, String message) {
        NotificationEntity entity = new NotificationEntity();
        entity.setRecipientUsername(username);
        entity.setMessage(message);
        entity.setRead(false);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private NotificationResponse response(NotificationEntity entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getRecipientUsername(),
                entity.getMessage(),
                entity.isRead(),
                entity.getCreatedAt().toString()
        );
    }

    private String fullName(UserProfileEntity profile) {
        String fullName = ((profile.getFirstName() == null ? "" : profile.getFirstName()) + " "
                + (profile.getLastName() == null ? "" : profile.getLastName())).trim();
        return fullName.isBlank() ? profile.getUsername() : fullName;
    }
}
