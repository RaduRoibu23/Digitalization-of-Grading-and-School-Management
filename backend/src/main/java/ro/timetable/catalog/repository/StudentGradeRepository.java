package ro.timetable.catalog.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.catalog.entity.StudentGradeEntity;

public interface StudentGradeRepository extends JpaRepository<StudentGradeEntity, Long> {

    List<StudentGradeEntity> findAllByOrderByStudentUsernameAscSubjectNameAscGradeDateDescIdDesc();
}
