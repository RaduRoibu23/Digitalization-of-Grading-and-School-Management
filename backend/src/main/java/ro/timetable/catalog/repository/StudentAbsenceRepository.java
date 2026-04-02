package ro.timetable.catalog.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.catalog.entity.StudentAbsenceEntity;

public interface StudentAbsenceRepository extends JpaRepository<StudentAbsenceEntity, Long> {

    List<StudentAbsenceEntity> findAllByOrderByStudentUsernameAscSubjectNameAscAbsenceDateDescIdDesc();
}
