package ro.timetable.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequestEntity, Long> {

    List<DocumentRequestEntity> findByStudentUsernameOrderByCreatedAtDescIdDesc(String studentUsername);

    List<DocumentRequestEntity> findAllByOrderByCreatedAtDescIdDesc();

    Optional<DocumentRequestEntity> findTopByDocumentTypeAndStatusAndDocumentNumberIsNotNullOrderByDocumentNumberDesc(
            String documentType,
            String status
    );
}
