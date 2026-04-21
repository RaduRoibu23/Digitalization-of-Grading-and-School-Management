package ro.timetable.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ro.timetable.audit.service.AuditService;
import ro.timetable.catalog.service.CatalogService;
import ro.timetable.common.dto.ApiDtos.CatalogResponse;
import ro.timetable.common.dto.ApiDtos.CatalogSubjectResponse;
import ro.timetable.common.dto.ApiDtos.DocumentRequestResponse;
import ro.timetable.common.dto.ApiDtos.ProfileResponse;
import ro.timetable.documents.entity.DocumentRequestEntity;
import ro.timetable.documents.repository.DocumentRequestRepository;
import ro.timetable.notifications.service.NotificationService;
import ro.timetable.reference.model.SchoolClass;
import ro.timetable.reference.model.UserProfile;
import ro.timetable.reference.service.SchoolDataService;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SchoolDataService schoolDataService;

    @Mock
    private CatalogService catalogService;

    @Mock
    private DocumentRequestRepository documentRequestRepository;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                auditService,
                notificationService,
                schoolDataService,
                catalogService,
                documentRequestRepository,
                new ObjectMapper()
        );
    }

    @Test
    void parentRequestUsesLinkedStudentAndNotifiesAcademicRecipients() {
        UserProfile studentProfile = new UserProfile(1L, 1, "student001", "student", "Ana", "Popescu", "student001@timetable.local", "Campulung", null, null, null, null, 10L, "X A", List.of(), null, false);
        when(schoolDataService.resolveAcademicStudentProfile("parinte001", List.of("parent"))).thenReturn(studentProfile);
        when(schoolDataService.academicNotificationRecipients("student001")).thenReturn(List.of("student001", "parinte001"));
        when(schoolDataService.getUserProfilesByRole("secretariat")).thenReturn(List.of());
        when(schoolDataService.getUserProfilesByRole("sysadmin")).thenReturn(List.of());
        when(documentRequestRepository.save(any(DocumentRequestEntity.class))).thenAnswer(invocation -> {
            DocumentRequestEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return entity;
        });

        DocumentRequestResponse response = documentService.createStudentRequest(
                "parinte001",
                List.of("parent"),
                DocumentService.TYPE_TRANSCRIPT,
                "bursa"
        );

        assertThat(response.student_username()).isEqualTo("student001");
        assertThat(response.requested_by_username()).isEqualTo("parinte001");

        ArgumentCaptor<List<String>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService, times(2)).createNotifications(recipientsCaptor.capture(), any(NotificationService.NotificationPayload.class));
        assertThat(recipientsCaptor.getAllValues()).contains(List.of("student001", "parinte001"));
    }

    @Test
    void transcriptSnapshotAddsOverallAverageAndTotalRow() {
        DocumentRequestEntity entity = new DocumentRequestEntity();
        entity.setId(11L);
        entity.setDocumentType(DocumentService.TYPE_TRANSCRIPT);
        entity.setStudentUsername("student001");
        entity.setSeries("FMT");
        entity.setDocumentNumber(12);
        entity.setPurpose("bursa");
        entity.setReviewedAt(Instant.parse("2026-04-18T10:15:30Z"));

        UserProfile studentProfile = new UserProfile(1L, 1, "student001", "student", "Ana", "Popescu", "student001@timetable.local", "Campulung", null, null, null, null, 10L, "X A", List.of(), null, false);
        SchoolClass schoolClass = new SchoolClass(10L, "X A", "Matematica-Informatica", null, null);

        when(schoolDataService.getProfile("student001")).thenReturn(studentProfile);
        when(schoolDataService.getClassById(10L)).thenReturn(schoolClass);
        when(catalogService.getCatalogForStudent("student001", List.of("student"), "student001")).thenReturn(new CatalogResponse(
                new ProfileResponse(1L, 1, "student001", "student", "Ana", "Popescu", "student001@timetable.local", null, null, null, null, null, 10L, "X A", "Matematica-Informatica", List.of(), null, null, null, null, null, null, false),
                List.of(
                        new CatalogSubjectResponse(1L, "Matematica", 4, 5, 8.5, List.of("Mihai Ionescu"), List.of(), List.of(), false),
                        new CatalogSubjectResponse(2L, "Informatica", 4, 5, 9.5, List.of("Irina Marin"), List.of(), List.of(), false),
                        new CatalogSubjectResponse(3L, "Sport", 2, 3, null, List.of("Sportiv"), List.of(), List.of(), false)
                ),
                false
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = ReflectionTestUtils.invokeMethod(documentService, "buildTranscriptSnapshot", entity);
        String rows = ReflectionTestUtils.invokeMethod(documentService, "transcriptRows", snapshot);

        assertThat(snapshot.get("overall_average")).isEqualTo("9.00");
        assertThat(rows).contains("Media totala");
        assertThat(rows).contains("9.00");
    }
}
