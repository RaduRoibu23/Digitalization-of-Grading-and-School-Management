package ro.timetable.audit.service;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.timetable.audit.entity.AuditLogEntity;
import ro.timetable.audit.repository.AuditLogRepository;
import ro.timetable.common.dto.ApiDtos.AuditEntryResponse;

@Service
public class AuditService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(String action, String actorUsername, String effect) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAction(action);
        entity.setActorUsername(actorUsername == null || actorUsername.isBlank() ? "necunoscut" : actorUsername);
        entity.setEffect(effect == null || effect.isBlank() ? "-" : effect);
        entity.setCreatedAt(Instant.now());
        auditLogRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> latest(Integer limit) {
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return auditLogRepository.findAll(PageRequest.of(
                        0,
                        safeLimit,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                ))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditEntryResponse toResponse(AuditLogEntity entity) {
        return new AuditEntryResponse(
                entity.getId(),
                entity.getAction(),
                entity.getActorUsername(),
                entity.getEffect(),
                entity.getCreatedAt().toString()
        );
    }
}
