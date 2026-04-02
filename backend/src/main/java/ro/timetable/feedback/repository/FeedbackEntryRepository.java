package ro.timetable.feedback.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.feedback.entity.FeedbackEntryEntity;

public interface FeedbackEntryRepository extends JpaRepository<FeedbackEntryEntity, Long> {

    List<FeedbackEntryEntity> findAllByOrderByCreatedAtDescIdDesc();

    List<FeedbackEntryEntity> findBySubmittedByUsernameOrderByCreatedAtDescIdDesc(String submittedByUsername);

    Optional<FeedbackEntryEntity> findByIdAndSubmittedByUsername(Long id, String submittedByUsername);
}
