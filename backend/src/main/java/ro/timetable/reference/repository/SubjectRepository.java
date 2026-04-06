package ro.timetable.reference.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.reference.entity.SubjectEntity;

public interface SubjectRepository extends JpaRepository<SubjectEntity, Long> {

    List<SubjectEntity> findByNameIn(Collection<String> names);
}
