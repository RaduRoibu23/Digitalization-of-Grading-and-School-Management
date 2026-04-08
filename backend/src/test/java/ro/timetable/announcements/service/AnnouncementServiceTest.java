package ro.timetable.announcements.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.announcements.entity.AnnouncementEntity;
import ro.timetable.announcements.repository.AnnouncementRepository;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.AnnouncementResponse;
import ro.timetable.reference.service.SchoolDataService;

class AnnouncementServiceTest {

    @Test
    void blocksStudentsFromPublishing() {
        AnnouncementRepository repository = mock(AnnouncementRepository.class);
        AuditService auditService = mock(AuditService.class);
        SchoolDataService schoolDataService = mock(SchoolDataService.class);
        AnnouncementService service = new AnnouncementService(repository, auditService, schoolDataService);

        assertThatThrownBy(() -> service.createAnnouncement("student001", List.of("student"), "Titlu", "Mesaj"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void publishesAnnouncementForProfessor() {
        AnnouncementRepository repository = mock(AnnouncementRepository.class);
        AuditService auditService = mock(AuditService.class);
        SchoolDataService schoolDataService = mock(SchoolDataService.class);
        AnnouncementService service = new AnnouncementService(repository, auditService, schoolDataService);

        AnnouncementEntity saved = new AnnouncementEntity();
        saved.setId(7L);
        saved.setTitle("Sala 12 indisponibila");
        saved.setMessage("Astazi sala 12 este indisponibila pentru ultimele doua intervale.");
        saved.setCreatedByUsername("prof01");
        saved.setCreatedAt(Instant.parse("2026-04-08T12:00:00Z"));

        when(repository.save(any(AnnouncementEntity.class))).thenReturn(saved);

        AnnouncementResponse response = service.createAnnouncement(
                "prof01",
                List.of("professor"),
                "Sala 12 indisponibila",
                "Astazi sala 12 este indisponibila pentru ultimele doua intervale."
        );

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.created_by_username()).isEqualTo("prof01");
        verify(auditService).record("Publicare anunt", "prof01", "A fost publicat un anunt nou in dashboard");
    }

    @Test
    void blocksNonSysadminFromDeletingAnnouncement() {
        AnnouncementRepository repository = mock(AnnouncementRepository.class);
        AuditService auditService = mock(AuditService.class);
        SchoolDataService schoolDataService = mock(SchoolDataService.class);
        AnnouncementService service = new AnnouncementService(repository, auditService, schoolDataService);

        assertThatThrownBy(() -> service.deleteAnnouncement("prof01", List.of("professor"), 11L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verify(repository, never()).delete(any(AnnouncementEntity.class));
    }

    @Test
    void deletesAnnouncementForSysadmin() {
        AnnouncementRepository repository = mock(AnnouncementRepository.class);
        AuditService auditService = mock(AuditService.class);
        SchoolDataService schoolDataService = mock(SchoolDataService.class);
        AnnouncementService service = new AnnouncementService(repository, auditService, schoolDataService);

        AnnouncementEntity stored = new AnnouncementEntity();
        stored.setId(11L);
        stored.setTitle("Anunt intern");
        stored.setMessage("Mesaj");
        stored.setCreatedByUsername("secretariat01");
        stored.setCreatedAt(Instant.parse("2026-04-08T12:00:00Z"));

        when(repository.findById(11L)).thenReturn(Optional.of(stored));

        ActionResponse response = service.deleteAnnouncement("sysadmin01", List.of("sysadmin"), 11L);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.detail()).isEqualTo("Anuntul a fost sters.");
        verify(repository).delete(stored);
        verify(auditService).record("Stergere anunt", "sysadmin01", "A fost sters anuntul #11");
    }
}
