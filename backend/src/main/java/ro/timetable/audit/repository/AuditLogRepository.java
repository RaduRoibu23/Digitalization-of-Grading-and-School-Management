package ro.timetable.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.audit.entity.AuditLogEntity;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}
