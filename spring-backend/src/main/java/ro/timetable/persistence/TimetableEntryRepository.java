package ro.timetable.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntryEntity, Long> {

    List<TimetableEntryEntity> findAllByOrderByClassIdAscWeekdayAscIndexInDayAsc();

    void deleteByClassId(Long classId);
}
