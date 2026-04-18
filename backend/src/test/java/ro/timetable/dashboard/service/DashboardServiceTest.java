package ro.timetable.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.timetable.announcements.service.AnnouncementService;
import ro.timetable.common.dto.ApiDtos.DashboardSummaryResponse;
import ro.timetable.common.dto.ApiDtos.DocumentRequestResponse;
import ro.timetable.common.dto.ApiDtos.FeedbackEntryResponse;
import ro.timetable.common.dto.ApiDtos.MeResponse;
import ro.timetable.common.dto.ApiDtos.NotificationResponse;
import ro.timetable.common.dto.ApiDtos.AnnouncementResponse;
import ro.timetable.common.dto.ApiDtos.ProfileSettingsResponse;
import ro.timetable.common.dto.ApiDtos.UnreadNotificationCountResponse;
import ro.timetable.documents.service.DocumentService;
import ro.timetable.feedback.service.FeedbackService;
import ro.timetable.notifications.service.NotificationService;
import ro.timetable.reference.service.SchoolDataService;
import ro.timetable.timetable.model.TimetableEntry;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AnnouncementService announcementService;

    @Mock
    private DocumentService documentService;

    @Mock
    private FeedbackService feedbackService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SchoolDataService schoolDataService;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void buildsAcademicSummaryWithoutOperationalData() {
        MeResponse me = new MeResponse(
                1L,
                1,
                "student001",
                "Ana",
                "Popescu",
                "student001@timetable.local",
                "Campulung",
                null,
                null,
                null,
                null,
                "student",
                List.of("student"),
                10L,
                "X A",
                "Filologie",
                List.of(),
                Map.of(),
                new ProfileSettingsResponse(true, true),
                null,
                null,
                null,
                null,
                null
        );

        when(schoolDataService.meResponse("student001", List.of("student"), Map.of())).thenReturn(me);
        when(notificationService.getNotificationsForUser("student001", false, 4)).thenReturn(List.of(
                new NotificationResponse(1L, "student001", "Noutate", "system", null, "Mesaj", false, null, "2026-04-08T10:00:00Z")
        ));
        when(notificationService.getUnreadCount("student001")).thenReturn(new UnreadNotificationCountResponse(2));
        when(schoolDataService.getTimetableForClass(10L)).thenReturn(List.of());
        when(announcementService.listAnnouncements("student001", List.of("student"), 6)).thenReturn(List.of(
                new AnnouncementResponse(3L, "Anunt", "Mesaj intern", "prof01", "2026-04-08T10:10:00Z")
        ));
        when(announcementService.canPublish(List.of("student"))).thenReturn(false);

        DashboardSummaryResponse summary = dashboardService.buildSummary("student001", List.of("student"), Map.of());

        assertThat(summary.role_context()).isEqualTo("academic");
        assertThat(summary.quick_actions()).hasSize(3);
        assertThat(summary.announcements()).hasSize(1);
        assertThat(summary.can_publish_announcements()).isFalse();
        assertThat(summary.recent_notifications()).hasSize(1);
        assertThat(summary.pending_documents()).isEmpty();
        assertThat(summary.recent_feedback()).isEmpty();
    }

    @Test
    void buildsOperationsSummaryWithPendingWork() {
        MeResponse me = new MeResponse(
                2L,
                1,
                "sysadmin01",
                "Radu",
                "Admin",
                "sysadmin01@timetable.local",
                null,
                null,
                null,
                null,
                null,
                "sysadmin",
                List.of("sysadmin"),
                null,
                null,
                null,
                List.of(),
                Map.of(),
                new ProfileSettingsResponse(true, true),
                null,
                null,
                null,
                null,
                null
        );

        when(schoolDataService.meResponse("sysadmin01", List.of("sysadmin"), Map.of())).thenReturn(me);
        when(notificationService.getNotificationsForUser("sysadmin01", false, 4)).thenReturn(List.of());
        when(notificationService.getUnreadCount("sysadmin01")).thenReturn(new UnreadNotificationCountResponse(0));
        when(announcementService.listAnnouncements("sysadmin01", List.of("sysadmin"), 6)).thenReturn(List.of());
        when(announcementService.canPublish(List.of("sysadmin"))).thenReturn(true);
        when(documentService.listRequests("sysadmin01", List.of("sysadmin"))).thenReturn(List.of(
                new DocumentRequestResponse(1L, "student_certificate", "Adeverinta de elev", "PENDING", "bursa", null, null, "student001", "student001", null, null, "2026-04-08T08:00:00Z", null, true, false)
        ));
        when(feedbackService.listEntries("sysadmin01", List.of("sysadmin"))).thenReturn(List.of(
                new FeedbackEntryResponse(11L, "orar", "Orar", "negativa", "Negativa", true, "UNOPENED", "Nedeschis", "Mesaj", null, "student001", null, null, "2026-04-08T07:00:00Z", null, null, true, true)
        ));

        DashboardSummaryResponse summary = dashboardService.buildSummary("sysadmin01", List.of("sysadmin"), Map.of());

        assertThat(summary.role_context()).isEqualTo("operations");
        assertThat(summary.pending_documents()).hasSize(1);
        assertThat(summary.recent_feedback()).hasSize(1);
        assertThat(summary.can_publish_announcements()).isTrue();
        assertThat(summary.quick_actions()).extracting("path")
                .contains("/app/orar-pe-clasa", "/app/genereaza-orar", "/app/utilizatori", "/app/documente");
    }

    @Test
    void buildsAcademicSummaryForParentUsingLinkedStudentClass() {
        MeResponse me = new MeResponse(
                3L,
                1,
                "parinte001",
                "Parinte",
                "Popescu",
                "parinte001@timetable.local",
                null,
                null,
                null,
                null,
                null,
                "parent",
                List.of("parent"),
                null,
                null,
                null,
                List.of(),
                Map.of(),
                new ProfileSettingsResponse(true, true),
                null,
                "student001",
                "Ana Popescu",
                10L,
                "X A"
        );

        when(schoolDataService.meResponse("parinte001", List.of("parent"), Map.of())).thenReturn(me);
        when(notificationService.getNotificationsForUser("parinte001", false, 4)).thenReturn(List.of());
        when(notificationService.getUnreadCount("parinte001")).thenReturn(new UnreadNotificationCountResponse(1));
        when(announcementService.listAnnouncements("parinte001", List.of("parent"), 6)).thenReturn(List.of());
        when(announcementService.canPublish(List.of("parent"))).thenReturn(false);
        when(schoolDataService.getTimetableForClass(10L)).thenReturn(List.of(
                new TimetableEntry(1L, 10L, "X A", 1L, "Matematica", 101L, "Sala 1", "mate01", "Mihai Ionescu", LocalDate.now().getDayOfWeek().getValue(), 2, 1)
        ));

        DashboardSummaryResponse summary = dashboardService.buildSummary("parinte001", List.of("parent"), Map.of());

        assertThat(summary.role_context()).isEqualTo("academic");
        assertThat(summary.title()).isEqualTo("Panoul parintelui");
        verify(schoolDataService).getTimetableForClass(10L);
    }
}
