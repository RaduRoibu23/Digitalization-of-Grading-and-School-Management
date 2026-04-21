package ro.timetable.catalog.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.catalog.entity.GradeChangeRequestEntity;

public interface GradeChangeRequestRepository extends JpaRepository<GradeChangeRequestEntity, Long> {

    List<GradeChangeRequestEntity> findAllByOrderByCreatedAtDescIdDesc();

    List<GradeChangeRequestEntity> findByStatusOrderByCreatedAtDescIdDesc(String status);

    List<GradeChangeRequestEntity> findByRequestedByUsernameOrderByCreatedAtDescIdDesc(String requestedByUsername);

    Optional<GradeChangeRequestEntity> findByGradeIdAndStatus(Long gradeId, String status);
}
