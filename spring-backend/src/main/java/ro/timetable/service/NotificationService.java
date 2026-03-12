package ro.timetable.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.model.AppNotification;
import ro.timetable.persistence.NotificationEntity;
import ro.timetable.persistence.NotificationRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Map<String, Object>> getNotificationsForUser(String username, boolean unreadOnly) {
        List<NotificationEntity> notifications = unreadOnly
                ? notificationRepository.findByRecipientUsernameAndReadFalseOrderByCreatedAtDescIdDesc(username)
                : notificationRepository.findByRecipientUsernameOrderByCreatedAtDescIdDesc(username);

        return notifications.stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public Map<String, Object> markAsRead(String username, Long notificationId) {
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
    public Map<String, Object> sendToUser(String username, String message) {
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

    private Map<String, Object> response(NotificationEntity entity) {
        AppNotification notification = new AppNotification(
                entity.getId(),
                entity.getRecipientUsername(),
                entity.getMessage(),
                entity.isRead(),
                entity.getCreatedAt().toString()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", notification.id());
        response.put("recipient_username", notification.recipientUsername());
        response.put("message", notification.message());
        response.put("read", notification.read());
        response.put("created_at", notification.createdAt());
        return response;
    }
}
