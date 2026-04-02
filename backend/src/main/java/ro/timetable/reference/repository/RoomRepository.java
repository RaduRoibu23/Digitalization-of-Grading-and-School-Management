package ro.timetable.reference.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.reference.entity.RoomEntity;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
}
