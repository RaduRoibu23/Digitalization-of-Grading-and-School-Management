package ro.timetable.dashboard.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import ro.timetable.announcements.service.AnnouncementService;
import ro.timetable.common.dto.ApiDtos.AnnouncementResponse;
import ro.timetable.common.dto.ApiDtos.DashboardMetricResponse;
import ro.timetable.common.dto.ApiDtos.DashboardQuickActionResponse;
import ro.timetable.common.dto.ApiDtos.DashboardSummaryResponse;
import ro.timetable.common.dto.ApiDtos.DashboardTimetableEntryResponse;
import ro.timetable.common.dto.ApiDtos.DocumentRequestResponse;
import ro.timetable.common.dto.ApiDtos.FeedbackEntryResponse;
import ro.timetable.common.dto.ApiDtos.MeResponse;
import ro.timetable.common.dto.ApiDtos.NotificationResponse;
import ro.timetable.notifications.service.NotificationService;
import ro.timetable.documents.service.DocumentService;
import ro.timetable.feedback.service.FeedbackService;
import ro.timetable.reference.service.SchoolDataService;
import ro.timetable.timetable.model.TimetableEntry;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final int NOTIFICATION_LIMIT = 4;
    private static final int PENDING_DOCUMENT_LIMIT = 5;
    private static final int FEEDBACK_LIMIT = 5;
    private static final int ANNOUNCEMENT_LIMIT = 6;

    private static final Map<Integer, String> TIME_LABELS = Map.ofEntries(
            Map.entry(1, "08:00 - 08:50"),
            Map.entry(2, "09:00 - 09:50"),
            Map.entry(3, "10:00 - 10:50"),
            Map.entry(4, "11:00 - 11:50"),
            Map.entry(5, "12:00 - 12:50"),
            Map.entry(6, "13:00 - 13:50"),
            Map.entry(7, "14:00 - 14:50")
    );

    private static final Map<Integer, LocalTime> SLOT_END_TIMES = Map.ofEntries(
            Map.entry(1, LocalTime.of(8, 50)),
            Map.entry(2, LocalTime.of(9, 50)),
            Map.entry(3, LocalTime.of(10, 50)),
            Map.entry(4, LocalTime.of(11, 50)),
            Map.entry(5, LocalTime.of(12, 50)),
            Map.entry(6, LocalTime.of(13, 50)),
            Map.entry(7, LocalTime.of(14, 50))
    );

    private final AnnouncementService announcementService;
    private final DocumentService documentService;
    private final FeedbackService feedbackService;
    private final NotificationService notificationService;
    private final SchoolDataService schoolDataService;

    public DashboardService(
            AnnouncementService announcementService,
            DocumentService documentService,
            FeedbackService feedbackService,
            NotificationService notificationService,
            SchoolDataService schoolDataService
    ) {
        this.announcementService = announcementService;
        this.documentService = documentService;
        this.feedbackService = feedbackService;
        this.notificationService = notificationService;
        this.schoolDataService = schoolDataService;
    }

    public DashboardSummaryResponse buildSummary(String username, List<String> roles, Map<String, Object> claims) {
        MeResponse me = schoolDataService.meResponse(username, roles, claims);
        List<AnnouncementResponse> announcements = announcementService.listAnnouncements(
                me.username(),
                roles,
                ANNOUNCEMENT_LIMIT
        );
        List<NotificationResponse> recentNotifications = notificationService.getNotificationsForUser(
                me.username(),
                false,
                NOTIFICATION_LIMIT
        );
        long unreadNotifications = notificationService.getUnreadCount(me.username()).unread_count();

        if (roles.contains("student") || roles.contains("parent") || roles.contains("professor")) {
            return buildAcademicSummary(me, roles, unreadNotifications, recentNotifications, announcements);
        }

        return buildOperationsSummary(me, roles, unreadNotifications, recentNotifications, announcements);
    }

    private DashboardSummaryResponse buildAcademicSummary(
            MeResponse me,
            List<String> roles,
            long unreadNotifications,
            List<NotificationResponse> recentNotifications,
            List<AnnouncementResponse> announcements
    ) {
        List<TimetableEntry> sourceEntries = roles.contains("professor")
                ? schoolDataService.getTimetableForTeacher(me.username())
                : academicClassId(me) == null ? List.of() : schoolDataService.getTimetableForClass(academicClassId(me));

        int todayWeekday = schoolWeekday(LocalDate.now(ZoneId.systemDefault()).getDayOfWeek());
        List<TimetableEntry> todayEntries = sourceEntries.stream()
                .filter(entry -> entry.weekday() != null && entry.weekday() == todayWeekday)
                .sorted(Comparator.comparing(TimetableEntry::indexInDay))
                .toList();
        DashboardTimetableEntryResponse nextEntry = resolveNextEntry(todayEntries);
        List<DashboardMetricResponse> metrics = List.of(
                new DashboardMetricResponse(
                        "today-slots",
                        "Ore astazi",
                        String.valueOf(todayEntries.size()),
                        roles.contains("professor") ? "Programul tau de predare pentru ziua curenta." : "Intervalele planificate pentru ziua curenta.",
                        "primary"
                ),
                new DashboardMetricResponse(
                        "next-slot",
                        "Urmatorul interval",
                        nextEntry == null ? "Zi finalizata" : nextEntry.time_label(),
                        nextEntry == null ? "Nu mai exista alte ore programate astazi." : nextEntry.subject_name(),
                        nextEntry == null ? "neutral" : "accent"
                ),
                new DashboardMetricResponse(
                        "notifications",
                        "Notificari necitite",
                        String.valueOf(unreadNotifications),
                        unreadNotifications == 0 ? "Inbox-ul este la zi." : "Ai actualizari care merita verificate.",
                        unreadNotifications == 0 ? "neutral" : "warning"
                )
        );

        return new DashboardSummaryResponse(
                "academic",
                roles.contains("professor") ? "Panoul profesorului" : roles.contains("parent") ? "Panoul parintelui" : "Panoul elevului",
                roles.contains("professor")
                        ? "Vezi programul de astazi, urmatorul interval si actualizarile care necesita atentie."
                        : roles.contains("parent")
                        ? "Ai intr-un singur loc programul, catalogul, documentele si notificarile relevante pentru copilul asociat."
                        : "Toate informatiile esentiale din ziua curenta, fara sa intri in fiecare modul separat.",
                metrics,
                academicQuickActions(),
                announcements,
                announcementService.canPublish(roles),
                todayEntries.stream().map(this::toDashboardTimetableEntry).toList(),
                nextEntry,
                recentNotifications,
                List.of(),
                List.of()
        );
    }

    private DashboardSummaryResponse buildOperationsSummary(
            MeResponse me,
            List<String> roles,
            long unreadNotifications,
            List<NotificationResponse> recentNotifications,
            List<AnnouncementResponse> announcements
    ) {
        List<DocumentRequestResponse> pendingDocuments = canReviewDocuments(roles)
                ? documentService.listRequests(me.username(), roles).stream()
                .filter(request -> "PENDING".equals(request.status()))
                .limit(PENDING_DOCUMENT_LIMIT)
                .toList()
                : List.of();
        List<FeedbackEntryResponse> recentFeedback = canReviewFeedback(roles)
                ? feedbackService.listEntries(me.username(), roles).stream()
                .filter(entry -> !"RESOLVED".equals(entry.status()))
                .limit(FEEDBACK_LIMIT)
                .toList()
                : List.of();

        List<DashboardMetricResponse> metrics = List.of(
                new DashboardMetricResponse(
                        "documents",
                        "Cereri documente in asteptare",
                        String.valueOf(pendingDocuments.size()),
                        pendingDocuments.isEmpty() ? "Nu exista cereri noi in acest moment." : "Solicitari care asteapta procesare.",
                        pendingDocuments.isEmpty() ? "neutral" : "warning"
                ),
                new DashboardMetricResponse(
                        "feedback",
                        "Cereri active de asistenta",
                        String.valueOf(recentFeedback.size()),
                        recentFeedback.isEmpty() ? "Fluxul de asistenta este la zi." : "Mesaje care trebuie urmarite sau inchise.",
                        recentFeedback.isEmpty() ? "neutral" : "accent"
                ),
                new DashboardMetricResponse(
                        "notifications",
                        "Notificari necitite",
                        String.valueOf(unreadNotifications),
                        unreadNotifications == 0 ? "Nu exista notificari restante." : "Platforma are actualizari noi pentru tine.",
                        unreadNotifications == 0 ? "neutral" : "primary"
                )
        );

        return new DashboardSummaryResponse(
                "operations",
                "Panou operational",
                "Actiunile rapide, solicitarile in asteptare si semnalele importante sunt adunate intr-un singur ecran.",
                metrics,
                operationsQuickActions(roles),
                announcements,
                announcementService.canPublish(roles),
                List.of(),
                null,
                recentNotifications,
                pendingDocuments,
                recentFeedback
        );
    }

    private List<DashboardQuickActionResponse> academicQuickActions() {
        return List.of(
                new DashboardQuickActionResponse(
                        "Orarul meu",
                        "/app/orarul-meu",
                        "Deschide imediat programul complet al saptamanii curente.",
                        "timetable"
                ),
                new DashboardQuickActionResponse(
                        "Catalog",
                        "/app/catalog",
                        "Vezi notele, absentele si materiile intr-un singur loc.",
                        "catalog"
                ),
                new DashboardQuickActionResponse(
                        "Asistenta",
                        "/app/feedback",
                        "Trimite rapid o cerere sau verifica raspunsurile primite.",
                        "feedback"
                )
        );
    }

    private List<DashboardQuickActionResponse> operationsQuickActions(List<String> roles) {
        List<DashboardQuickActionResponse> actions = new ArrayList<>();
        if (hasAnyRole(roles, "secretariat", "scheduler", "director", "sysadmin")) {
            actions.add(new DashboardQuickActionResponse(
                    "Orar pe clasa",
                    "/app/orar-pe-clasa",
                    "Verifica orarele existente si descarca imediat PDF-ul potrivit.",
                    "timetable"
            ));
            actions.add(new DashboardQuickActionResponse(
                    "Genereaza orar",
                    "/app/genereaza-orar",
                    "Intra in consola de planificare si ajusteaza manual intervalele dorite.",
                    "timetable"
            ));
        }
        if (hasAnyRole(roles, "secretariat", "director", "sysadmin")) {
            actions.add(new DashboardQuickActionResponse(
                    "Utilizatori",
                    "/app/utilizatori",
                    "Administreaza profilele si vezi rapid situatia claselor.",
                    "director"
            ));
            actions.add(new DashboardQuickActionResponse(
                    "Documente",
                    "/app/documente",
                    "Proceseaza cereri, aproba documente si descarca PDF-urile emise.",
                    "documents"
            ));
        }
        if (hasAnyRole(roles, "secretariat", "director", "sysadmin")) {
            actions.add(new DashboardQuickActionResponse(
                    "Asistenta",
                    "/app/feedback",
                    "Urmareste mesajele active si raspunde direct din fluxul de asistenta.",
                    "feedback"
            ));
        }
        return actions;
    }

    private DashboardTimetableEntryResponse resolveNextEntry(List<TimetableEntry> todayEntries) {
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        return todayEntries.stream()
                .sorted(Comparator.comparing(TimetableEntry::indexInDay))
                .map(this::toDashboardTimetableEntry)
                .filter(entry -> {
                    LocalTime endTime = SLOT_END_TIMES.get(entry.index_in_day());
                    return endTime != null && !endTime.isBefore(now);
                })
                .findFirst()
                .orElse(null);
    }

    private DashboardTimetableEntryResponse toDashboardTimetableEntry(TimetableEntry entry) {
        return new DashboardTimetableEntryResponse(
                entry.id(),
                entry.subjectName(),
                entry.className(),
                entry.roomName(),
                entry.teacherName(),
                entry.weekday(),
                entry.indexInDay(),
                TIME_LABELS.getOrDefault(entry.indexInDay(), "Interval")
        );
    }

    private int schoolWeekday(DayOfWeek dayOfWeek) {
        int value = dayOfWeek.getValue();
        return value >= 1 && value <= 5 ? value : 0;
    }

    private boolean canReviewDocuments(List<String> roles) {
        return hasAnyRole(roles, "secretariat", "sysadmin");
    }

    private boolean canReviewFeedback(List<String> roles) {
        return hasAnyRole(roles, "secretariat", "director", "sysadmin");
    }

    private boolean hasAnyRole(List<String> roles, String... expectedRoles) {
        for (String expectedRole : expectedRoles) {
            if (roles.contains(expectedRole)) {
                return true;
            }
        }
        return false;
    }

    private Long academicClassId(MeResponse me) {
        return me.class_id() != null ? me.class_id() : me.linked_student_class_id();
    }
}
