package ro.timetable.reference.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.reference.entity.SchoolClassEntity;

public interface SchoolClassRepository extends JpaRepository<SchoolClassEntity, Long> {
}
