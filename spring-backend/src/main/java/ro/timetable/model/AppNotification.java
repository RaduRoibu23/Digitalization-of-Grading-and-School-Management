package ro.timetable.model;

public record AppNotification(
        Long id,
        String recipientUsername,
        String message,
        boolean read,
        String createdAt
) {
}
