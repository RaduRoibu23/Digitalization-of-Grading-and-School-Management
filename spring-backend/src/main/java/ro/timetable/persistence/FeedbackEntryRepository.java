package ro.timetable.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackEntryRepository extends JpaRepository<FeedbackEntryEntity, Long> {

    List<FeedbackEntryEntity> findAllByOrderByCreatedAtDescIdDesc();

    List<FeedbackEntryEntity> findBySubmittedByUsernameOrderByCreatedAtDescIdDesc(String submittedByUsername);

    Optional<FeedbackEntryEntity> findByIdAndSubmittedByUsername(Long id, String submittedByUsername);
}
