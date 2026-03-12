package ro.timetable.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentGradeRepository extends JpaRepository<StudentGradeEntity, Long> {

    List<StudentGradeEntity> findAllByOrderByStudentUsernameAscSubjectNameAscGradeDateDescIdDesc();
}
