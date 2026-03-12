package ro.timetable.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.persistence.NotificationEntity;
import ro.timetable.persistence.NotificationRepository;
import ro.timetable.web.dto.ApiDtos.NotificationResponse;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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

        List<NotificationEntity> entities = usernames.stream()
                .filter(username -> username != null && !username.isBlank())
                .distinct()
                .map(username -> toEntity(username, message))
                .toList();

        if (!entities.isEmpty()) {
            notificationRepository.saveAll(entities);
        }
    }

    @Transactional
    public NotificationResponse sendToUser(String username, String message) {
        NotificationEntity entity = notificationRepository.save(toEntity(username, message));
        return response(entity);
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
}