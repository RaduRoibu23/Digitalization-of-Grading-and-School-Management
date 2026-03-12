package ro.timetable.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByRecipientUsernameOrderByCreatedAtDescIdDesc(String recipientUsername);

    List<NotificationEntity> findByRecipientUsernameAndReadFalseOrderByCreatedAtDescIdDesc(String recipientUsername);

    Optional<NotificationEntity> findByIdAndRecipientUsername(Long id, String recipientUsername);
}
