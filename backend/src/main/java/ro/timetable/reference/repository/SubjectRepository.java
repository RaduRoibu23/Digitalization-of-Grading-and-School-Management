package ro.timetable.reference.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.reference.entity.SubjectEntity;

public interface SubjectRepository extends JpaRepository<SubjectEntity, Long> {
}
