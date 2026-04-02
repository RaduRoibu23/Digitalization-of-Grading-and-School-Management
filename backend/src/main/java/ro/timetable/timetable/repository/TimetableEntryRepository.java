package ro.timetable.timetable.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.timetable.entity.TimetableEntryEntity;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntryEntity, Long> {

    List<TimetableEntryEntity> findAllByOrderByClassIdAscWeekdayAscIndexInDayAsc();

    void deleteByClassId(Long classId);
}
