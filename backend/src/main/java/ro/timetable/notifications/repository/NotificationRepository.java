package ro.timetable.notifications.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.notifications.entity.NotificationEntity;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByRecipientUsernameOrderByCreatedAtDescIdDesc(String recipientUsername);

    List<NotificationEntity> findByRecipientUsernameAndReadFalseOrderByCreatedAtDescIdDesc(String recipientUsername);

    List<NotificationEntity> findByRecipientUsernameOrderByCreatedAtDescIdDesc(String recipientUsername, Pageable pageable);

    List<NotificationEntity> findByRecipientUsernameAndReadFalseOrderByCreatedAtDescIdDesc(String recipientUsername, Pageable pageable);

    Optional<NotificationEntity> findByIdAndRecipientUsername(Long id, String recipientUsername);

    long countByRecipientUsernameAndReadFalse(String recipientUsername);
}
