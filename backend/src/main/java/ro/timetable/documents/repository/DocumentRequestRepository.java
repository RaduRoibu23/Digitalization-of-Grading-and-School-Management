package ro.timetable.documents.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.documents.entity.DocumentRequestEntity;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequestEntity, Long> {

    List<DocumentRequestEntity> findByStudentUsernameOrderByCreatedAtDescIdDesc(String studentUsername);

    List<DocumentRequestEntity> findAllByOrderByCreatedAtDescIdDesc();

    Optional<DocumentRequestEntity> findTopByDocumentTypeAndStatusAndDocumentNumberIsNotNullOrderByDocumentNumberDesc(
            String documentType,
            String status
    );
}
