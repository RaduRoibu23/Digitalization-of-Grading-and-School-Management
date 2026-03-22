package ro.timetable.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentAbsenceRepository extends JpaRepository<StudentAbsenceEntity, Long> {

    List<StudentAbsenceEntity> findAllByOrderByStudentUsernameAscSubjectNameAscAbsenceDateDescIdDesc();
}
