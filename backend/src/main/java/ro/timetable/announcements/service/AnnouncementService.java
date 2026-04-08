package ro.timetable.announcements.service;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.announcements.entity.AnnouncementEntity;
import ro.timetable.announcements.repository.AnnouncementRepository;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.AnnouncementResponse;
import ro.timetable.reference.service.SchoolDataService;

@Service
public class AnnouncementService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final String DEFAULT_TITLE = "Anunt intern";

    private final AnnouncementRepository announcementRepository;
    private final AuditService auditService;
    private final SchoolDataService schoolDataService;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            AuditService auditService,
            SchoolDataService schoolDataService
    ) {
        this.announcementRepository = announcementRepository;
        this.auditService = auditService;
        this.schoolDataService = schoolDataService;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listAnnouncements(String username, List<String> roles, Integer limit) {
        ensureAuthenticated(username, roles);
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return announcementRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, safeLimit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(String username, List<String> roles, String title, String message) {
        ensureCanPublish(username, roles);
        schoolDataService.getProfile(username);

        AnnouncementEntity entity = new AnnouncementEntity();
        entity.setTitle(normalizeTitle(title));
        entity.setMessage(normalizeMessage(message));
        entity.setCreatedByUsername(username);
        entity.setCreatedAt(Instant.now());

        AnnouncementEntity saved = announcementRepository.save(entity);
        auditService.record(
                "Publicare anunt",
                username,
                "A fost publicat un anunt nou in dashboard"
        );
        return toResponse(saved);
    }

    @Transactional
    public ActionResponse deleteAnnouncement(String username, List<String> roles, Long announcementId) {
        ensureSysadmin(username, roles);
        schoolDataService.getProfile(username);

        AnnouncementEntity entity = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anuntul solicitat nu exista"));

        announcementRepository.delete(entity);
        auditService.record(
                "Stergere anunt",
                username,
                "A fost sters anuntul #" + announcementId
        );
        return new ActionResponse("Anuntul a fost sters.", announcementId, null);
    }

    public boolean canPublish(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(role -> !"student".equals(role));
    }

    private void ensureAuthenticated(String username, List<String> roles) {
        if (username == null || username.isBlank() || roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trebuie sa fii autentificat pentru a vedea anunturile");
        }
    }

    private void ensureCanPublish(String username, List<String> roles) {
        ensureAuthenticated(username, roles);
        if (!canPublish(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Elevii nu pot publica anunturi");
        }
    }

    private void ensureSysadmin(String username, List<String> roles) {
        ensureAuthenticated(username, roles);
        if (!roles.contains("sysadmin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar conturile sysadmin pot sterge anunturi");
        }
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return DEFAULT_TITLE;
        }
        String normalized = title.trim();
        if (normalized.isBlank()) {
            return DEFAULT_TITLE;
        }
        if (normalized.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titlul anuntului poate avea maximum 160 de caractere");
        }
        return normalized;
    }

    private String normalizeMessage(String message) {
        String normalized = message == null ? null : message.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesajul anuntului este obligatoriu");
        }
        if (normalized.length() > 1200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesajul anuntului poate avea maximum 1200 de caractere");
        }
        return normalized.replaceAll("\\s+", " ");
    }

    private AnnouncementResponse toResponse(AnnouncementEntity entity) {
        return new AnnouncementResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getCreatedByUsername(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString()
        );
    }
}
