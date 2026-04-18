package ro.timetable.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.CatalogResponse;
import ro.timetable.common.dto.ApiDtos.GradeResponse;
import ro.timetable.common.dto.ApiDtos.ProfileResponse;
import ro.timetable.common.util.PersistentStateService;
import ro.timetable.notifications.service.NotificationService;
import ro.timetable.reference.model.SchoolClass;
import ro.timetable.reference.model.Subject;
import ro.timetable.reference.model.UserProfile;
import ro.timetable.reference.service.CurriculumPlanService;
import ro.timetable.reference.service.SchoolDataService;
import ro.timetable.timetable.model.TimetableEntry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogServiceTest {

    @Mock
    private SchoolDataService schoolDataService;

    @Mock
    private CurriculumPlanService curriculumPlanService;

    @Mock
    private PersistentStateService persistentStateService;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    private CatalogService catalogService;
    private UserProfile studentProfile;
    private UserProfile parentProfile;
    private UserProfile professorProfile;
    private UserProfile secretariatProfile;
    private SchoolClass schoolClass;
    private TimetableEntry timetableEntry;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(
                schoolDataService,
                curriculumPlanService,
                persistentStateService,
                auditService,
                notificationService
        );

        studentProfile = new UserProfile(1L, 1, "student001", "student", "Ana", "Popescu", "student001@timetable.local", "Campulung", null, null, null, null, 10L, "X A", List.of(), null);
        parentProfile = new UserProfile(2L, 1, "parinte001", "parent", "Parinte", "Popescu", "parinte001@timetable.local", null, null, null, null, null, null, null, List.of(), "student001");
        professorProfile = new UserProfile(3L, 1, "mate01", "professor", "Mihai", "Ionescu", "mate01@timetable.local", null, null, null, null, null, null, null, List.of("Matematica"), null);
        secretariatProfile = new UserProfile(4L, 1, "secretariat01", "secretariat", "Secretariat", "User", "secretariat01@timetable.local", null, null, null, null, null, null, null, List.of(), null);
        schoolClass = new SchoolClass(10L, "X A", "Matematica-Informatica", null, null);
        timetableEntry = new TimetableEntry(101L, 10L, "X A", 1L, "Matematica", 201L, "Sala 1", "mate01", "Mihai Ionescu", 1, 1, 1);

        when(schoolDataService.getProfile("student001")).thenReturn(studentProfile);
        when(schoolDataService.getProfile("parinte001")).thenReturn(parentProfile);
        when(schoolDataService.getProfile("mate01")).thenReturn(professorProfile);
        when(schoolDataService.getProfile("secretariat01")).thenReturn(secretariatProfile);
        when(schoolDataService.getProfile("admin01")).thenReturn(new UserProfile(5L, 1, "admin01", "admin", "Admin", "User", "admin01@timetable.local", null, null, null, null, null, null, null, List.of(), null));

        when(schoolDataService.subjectIdByName("Matematica")).thenReturn(1L);
        when(schoolDataService.weeklyHoursForSubject(10L, "Matematica")).thenReturn(4);
        when(schoolDataService.getTimetableForClass(10L)).thenReturn(List.of(timetableEntry));
        when(schoolDataService.getClassById(10L)).thenReturn(schoolClass);
        when(schoolDataService.getClasses()).thenReturn(List.of(schoolClass));
        when(schoolDataService.getSubjects()).thenReturn(List.of(new Subject(1L, "Matematica")));
        when(schoolDataService.isParentOfStudent("parinte001", "student001")).thenReturn(true);
        when(schoolDataService.academicNotificationRecipients("student001")).thenReturn(List.of("student001", "parinte001"));
        when(curriculumPlanService.hoursForClass("X A", "Matematica-Informatica")).thenReturn(new LinkedHashMap<>(java.util.Map.of("Matematica", 4)));
        when(schoolDataService.toProfileResponse(studentProfile, false)).thenReturn(new ProfileResponse(
                studentProfile.id(),
                studentProfile.version(),
                studentProfile.username(),
                studentProfile.role(),
                studentProfile.firstName(),
                studentProfile.lastName(),
                studentProfile.email(),
                null,
                null,
                null,
                null,
                null,
                studentProfile.classId(),
                studentProfile.className(),
                schoolClass.profile(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void gradeCommentIsVisibleToParentButHiddenFromAdminAndNotificationsFanOut() {
        GradeResponse created = catalogService.createGrade(
                "mate01",
                List.of("professor"),
                "student001",
                "Matematica",
                9,
                "2026-04-10",
                "Lucreaza mai atent la exercitiile de algebra."
        );

        CatalogResponse parentCatalog = catalogService.getCatalogForStudent("parinte001", List.of("parent"), "student001");
        CatalogResponse adminCatalog = catalogService.getCatalogForStudent("admin01", List.of("admin"), "student001");

        assertThat(created.comment()).isEqualTo("Lucreaza mai atent la exercitiile de algebra.");
        assertThat(parentCatalog.subjects()).singleElement()
                .satisfies(subject -> assertThat(subject.grades()).singleElement()
                        .satisfies(grade -> assertThat(grade.comment()).isEqualTo("Lucreaza mai atent la exercitiile de algebra.")));
        assertThat(adminCatalog.subjects()).singleElement()
                .satisfies(subject -> assertThat(subject.grades()).singleElement()
                        .satisfies(grade -> assertThat(grade.comment()).isNull()));

        ArgumentCaptor<List<String>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createNotifications(recipientsCaptor.capture(), any(NotificationService.NotificationPayload.class));
        assertThat(recipientsCaptor.getValue()).containsExactly("student001", "parinte001");
    }

    @Test
    void parentMustProvideReasonWhenMotivatingAbsence() {
        var absence = catalogService.createAbsence(
                "secretariat01",
                List.of("secretariat"),
                "student001",
                "Matematica",
                "2026-04-11"
        );

        assertThatThrownBy(() -> catalogService.motivateAbsence(
                "parinte001",
                List.of("parent"),
                absence.id(),
                absence.version(),
                null
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
