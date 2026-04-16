package ro.timetable.catalog.service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.catalog.model.StudentAbsence;
import ro.timetable.catalog.model.StudentGrade;
import ro.timetable.common.dto.ApiDtos.AbsenceResponse;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.CatalogResponse;
import ro.timetable.common.dto.ApiDtos.CatalogSubjectResponse;
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

@Service
public class CatalogService {

    private record SeedTeacher(String username, String fullName) {
    }

    private static final long CATALOG_SEED = 20260402L;

    private final SchoolDataService schoolDataService;
    private final CurriculumPlanService curriculumPlanService;
    private final PersistentStateService persistentStateService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Map<String, List<StudentGrade>> gradesByStudentUsername = new LinkedHashMap<>();
    private final Map<String, List<StudentAbsence>> absencesByStudentUsername = new LinkedHashMap<>();
    private final AtomicLong gradeIds = new AtomicLong(9000);
    private final AtomicLong absenceIds = new AtomicLong(12000);

    public CatalogService(
            SchoolDataService schoolDataService,
            CurriculumPlanService curriculumPlanService,
            PersistentStateService persistentStateService,
            AuditService auditService,
            NotificationService notificationService
    ) {
        this.schoolDataService = schoolDataService;
        this.curriculumPlanService = curriculumPlanService;
        this.persistentStateService = persistentStateService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @PostConstruct
    void init() {
        loadPersistedGrades();
        loadPersistedAbsences();
        seedCatalogDataIfNeeded();
    }

    public List<ProfileResponse> getCatalogStudents(String requesterUsername, List<String> roles) {
        ensureCatalogVisible(roles);

        List<UserProfile> students;
        if (hasRole(roles, "student")) {
            students = List.of(requireStudentProfile(requesterUsername));
        } else if (hasRole(roles, "professor")) {
            students = getStudentsForProfessor(requesterUsername);
        } else {
            students = schoolDataService.getUserProfilesByRole("student");
        }

        return students.stream()
                .sorted(Comparator.comparing(UserProfile::className, Comparator.nullsLast(String::compareTo))
                        .thenComparing(UserProfile::lastName)
                        .thenComparing(UserProfile::firstName)
                        .thenComparing(UserProfile::username))
                .map(this::toProfileResponse)
                .toList();
    }

    public CatalogResponse getMyCatalog(String requesterUsername, List<String> roles) {
        ensureCatalogVisible(roles);
        if (!hasRole(roles, "student")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can access their own catalog endpoint");
        }
        return buildCatalogResponse(requireStudentProfile(requesterUsername), requesterUsername, roles);
    }

    public CatalogResponse getCatalogForStudent(String requesterUsername, List<String> roles, String studentUsername) {
        ensureCatalogVisible(roles);
        UserProfile student = requireStudentProfile(studentUsername);
        if (!canAccessStudentCatalog(requesterUsername, roles, student)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this catalog");
        }
        return buildCatalogResponse(student, requesterUsername, roles);
    }

    public GradeResponse createGrade(String requesterUsername, List<String> roles, String studentUsername, String subjectName, Integer gradeValue, String gradeDate) {
        if (!hasRole(roles, "secretariat") && !hasRole(roles, "professor")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only secretariat and professors can add grades");
        }

        ensureValidGradeValue(gradeValue);
        LocalDate.parse(gradeDate);
        UserProfile student = requireStudentProfile(studentUsername);
        Long subjectId = schoolDataService.subjectIdByName(subjectName);
        if (schoolDataService.weeklyHoursForSubject(student.classId(), subjectName) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Materia nu apartine clasei selectate");
        }

        TimetableEntry teacherAssignment = assignedTeacherForClassSubject(student.classId(), subjectId);
        if (teacherAssignment == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Genereaza mai intai orarul pentru materia selectata.");
        }
        if (!canAddGrade(requesterUsername, roles, student.classId(), subjectId, teacherAssignment)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to add a grade for this subject");
        }

        StudentGrade created = new StudentGrade(
                gradeIds.incrementAndGet(),
                student.username(),
                student.firstName() + " " + student.lastName(),
                student.classId(),
                student.className(),
                subjectId,
                subjectName,
                gradeValue,
                gradeDate,
                teacherAssignment.teacherUsername(),
                teacherAssignment.teacherName(),
                1
        );

        List<StudentGrade> studentGrades = gradesByStudentUsername.computeIfAbsent(student.username(), ignored -> new ArrayList<>());
        studentGrades.add(created);
        sortGrades(studentGrades);
        persistentStateService.saveGrade(created);
        notificationService.createNotifications(
                List.of(student.username()),
                new NotificationService.NotificationPayload(
                        "Catalog actualizat",
                        "Ai primit nota " + gradeValue + " la materia " + subjectName + ".",
                        "catalog",
                        "/app/catalog"
                )
        );
        auditService.record(
                "Adaugare nota",
                requesterUsername,
                "A fost adaugata nota " + gradeValue + " la " + subjectName + " pentru elevul " + student.username()
        );
        return gradeResponse(created, requesterUsername, roles);
    }

    public GradeResponse updateGrade(String requesterUsername, List<String> roles, Long gradeId, Integer version, Integer gradeValue, String gradeDate) {
        if (!hasRole(roles, "secretariat") && !hasRole(roles, "professor")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only secretariat and professors can update grades");
        }

        ensureValidGradeValue(gradeValue);
        LocalDate.parse(gradeDate);

        for (Map.Entry<String, List<StudentGrade>> bucket : gradesByStudentUsername.entrySet()) {
            List<StudentGrade> grades = bucket.getValue();
            for (int index = 0; index < grades.size(); index++) {
                StudentGrade existing = grades.get(index);
                if (!Objects.equals(existing.id(), gradeId)) {
                    continue;
                }
                if (!Objects.equals(existing.version(), version)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Nota a fost modificata intre timp. Da refresh si incearca din nou.");
                }
                if (!canEditGrade(requesterUsername, roles, existing)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to edit this grade");
                }

                StudentGrade updated = new StudentGrade(
                        existing.id(),
                        existing.studentUsername(),
                        existing.studentName(),
                        existing.classId(),
                        existing.className(),
                        existing.subjectId(),
                        existing.subjectName(),
                        gradeValue,
                        gradeDate,
                        existing.teacherUsername(),
                        existing.teacherName(),
                        existing.version() + 1
                );
                grades.set(index, updated);
                sortGrades(grades);
                persistentStateService.saveGrade(updated);
                notificationService.createNotifications(
                        List.of(existing.studentUsername()),
                        new NotificationService.NotificationPayload(
                                "Catalog actualizat",
                                "Nota la materia " + existing.subjectName() + " a fost actualizata. Valoarea curenta este " + gradeValue + ".",
                                "catalog",
                                "/app/catalog"
                        )
                );
                auditService.record(
                        "Actualizare nota",
                        requesterUsername,
                        "Nota " + updated.id() + " a fost actualizata la valoarea " + gradeValue + " pentru elevul " + updated.studentUsername()
                );
                return gradeResponse(updated, requesterUsername, roles);
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade not found");
    }

    public ActionResponse deleteGrade(String requesterUsername, List<String> roles, Long gradeId) {
        if (!hasRole(roles, "secretariat") && !hasRole(roles, "professor")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only secretariat and professors can delete grades");
        }

        for (Map.Entry<String, List<StudentGrade>> bucket : gradesByStudentUsername.entrySet()) {
            List<StudentGrade> grades = bucket.getValue();
            for (int index = 0; index < grades.size(); index++) {
                StudentGrade existing = grades.get(index);
                if (!Objects.equals(existing.id(), gradeId)) {
                    continue;
                }
                if (!canEditGrade(requesterUsername, roles, existing)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete this grade");
                }
                grades.remove(index);
                persistentStateService.deleteGrade(gradeId);
                notificationService.createNotifications(
                        List.of(existing.studentUsername()),
                        new NotificationService.NotificationPayload(
                                "Catalog actualizat",
                                "O nota la materia " + existing.subjectName() + " a fost stearsa.",
                                "catalog",
                                "/app/catalog"
                        )
                );
                auditService.record(
                        "Stergere nota",
                        requesterUsername,
                        "Nota " + gradeId + " a fost stearsa pentru elevul " + existing.studentUsername()
                );
                return new ActionResponse("Grade deleted", gradeId, null);
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade not found");
    }

    public AbsenceResponse createAbsence(String requesterUsername, List<String> roles, String studentUsername, String subjectName, String absenceDate) {
        if (!(hasRole(roles, "secretariat") || hasRole(roles, "professor") || hasRole(roles, "admin") || hasRole(roles, "sysadmin"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only secretariat and professors can add absences");
        }

        LocalDate.parse(absenceDate);
        UserProfile student = requireStudentProfile(studentUsername);
        Long subjectId = schoolDataService.subjectIdByName(subjectName);
        if (schoolDataService.weeklyHoursForSubject(student.classId(), subjectName) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Materia nu apartine clasei selectate");
        }

        TimetableEntry teacherAssignment = assignedTeacherForClassSubject(student.classId(), subjectId);
        if (teacherAssignment == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Genereaza mai intai orarul pentru materia selectata.");
        }
        if (!canAddAbsence(requesterUsername, roles, student.classId(), subjectId, teacherAssignment)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to add an absence for this subject");
        }

        ensureAbsenceNotDuplicated(student.username(), subjectId, absenceDate);
        UserProfile creator = schoolDataService.getProfile(requesterUsername);

        StudentAbsence created = new StudentAbsence(
                absenceIds.incrementAndGet(),
                student.username(),
                student.firstName() + " " + student.lastName(),
                student.classId(),
                student.className(),
                subjectId,
                subjectName,
                absenceDate,
                creator.username(),
                creator.firstName() + " " + creator.lastName(),
                false,
                null,
                null,
                null,
                1
        );

        List<StudentAbsence> studentAbsences = absencesByStudentUsername.computeIfAbsent(student.username(), ignored -> new ArrayList<>());
        studentAbsences.add(created);
        sortAbsences(studentAbsences);
        persistentStateService.saveAbsence(created);
        notificationService.createNotifications(
                List.of(student.username()),
                new NotificationService.NotificationPayload(
                        "Catalog actualizat",
                        "Ai primit o absenta la materia " + subjectName + ".",
                        "catalog",
                        "/app/catalog"
                )
        );
        auditService.record(
                "Adaugare absenta",
                requesterUsername,
                "A fost adaugata o absenta la " + subjectName + " pentru elevul " + student.username()
        );
        return absenceResponse(created, requesterUsername, roles);
    }

    public AbsenceResponse motivateAbsence(String requesterUsername, List<String> roles, Long absenceId, Integer version) {
        for (Map.Entry<String, List<StudentAbsence>> bucket : absencesByStudentUsername.entrySet()) {
            List<StudentAbsence> absences = bucket.getValue();
            for (int index = 0; index < absences.size(); index++) {
                StudentAbsence existing = absences.get(index);
                if (!Objects.equals(existing.id(), absenceId)) {
                    continue;
                }
                if (!Objects.equals(existing.version(), version)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Absenta a fost modificata intre timp. Da refresh si incearca din nou.");
                }
                if (!canMotivateAbsence(requesterUsername, roles, existing)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to motivate this absence");
                }
                if (existing.motivated()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Absenta este deja motivata");
                }

                UserProfile motivator = schoolDataService.getProfile(requesterUsername);
                StudentAbsence updated = new StudentAbsence(
                        existing.id(),
                        existing.studentUsername(),
                        existing.studentName(),
                        existing.classId(),
                        existing.className(),
                        existing.subjectId(),
                        existing.subjectName(),
                        existing.absenceDate(),
                        existing.teacherUsername(),
                        existing.teacherName(),
                        true,
                        motivator.username(),
                        motivator.firstName() + " " + motivator.lastName(),
                        LocalDate.now().toString(),
                        existing.version() + 1
                );
                absences.set(index, updated);
                sortAbsences(absences);
                persistentStateService.saveAbsence(updated);
                notificationService.createNotifications(
                        List.of(existing.studentUsername()),
                        new NotificationService.NotificationPayload(
                                "Catalog actualizat",
                                "O absenta la materia " + existing.subjectName() + " a fost motivata.",
                                "catalog",
                                "/app/catalog"
                        )
                );
                auditService.record(
                        "Motivare absenta",
                        requesterUsername,
                        "Absenta " + absenceId + " a fost motivata pentru elevul " + existing.studentUsername()
                );
                return absenceResponse(updated, requesterUsername, roles);
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence not found");
    }

    public void syncProfileData(UserProfile previousProfile, UserProfile updatedProfile) {
        if ("student".equals(updatedProfile.role())) {
            synchronizeStudentGrades(updatedProfile);
            synchronizeStudentAbsences(updatedProfile);
        }
        if ("professor".equals(updatedProfile.role())) {
            synchronizeTeacherGrades(updatedProfile);
            synchronizeTeacherAbsences(updatedProfile);
        }
    }

    private CatalogResponse buildCatalogResponse(UserProfile student, String requesterUsername, List<String> roles) {
        SchoolClass schoolClass = schoolDataService.getClassById(student.classId());
        LinkedHashMap<String, Integer> weeklyHours = curriculumPlanService.hoursForClass(schoolClass.name(), schoolClass.profile());
        LinkedHashMap<String, List<StudentGrade>> gradesBySubject = new LinkedHashMap<>();
        LinkedHashMap<String, List<StudentAbsence>> absencesBySubject = new LinkedHashMap<>();

        for (StudentGrade grade : gradesByStudentUsername.getOrDefault(student.username(), List.of())) {
            if (!canViewGrade(requesterUsername, roles, grade)) {
                continue;
            }
            gradesBySubject.computeIfAbsent(grade.subjectName(), ignored -> new ArrayList<>()).add(grade);
        }
        for (StudentAbsence absence : absencesByStudentUsername.getOrDefault(student.username(), List.of())) {
            if (!canViewAbsence(requesterUsername, roles, absence)) {
                continue;
            }
            absencesBySubject.computeIfAbsent(absence.subjectName(), ignored -> new ArrayList<>()).add(absence);
        }

        List<CatalogSubjectResponse> subjectRows = new ArrayList<>();
        for (Map.Entry<String, Integer> planEntry : weeklyHours.entrySet()) {
            String subjectName = planEntry.getKey();
            Long subjectId = schoolDataService.subjectIdByName(subjectName);
            TimetableEntry teacherAssignment = assignedTeacherForClassSubject(student.classId(), subjectId);
            List<StudentGrade> subjectGrades = gradesBySubject.getOrDefault(subjectName, List.of()).stream()
                    .sorted(Comparator.comparing(StudentGrade::gradeDate, Comparator.reverseOrder()).thenComparing(StudentGrade::id, Comparator.reverseOrder()))
                    .toList();
            List<StudentAbsence> subjectAbsences = absencesBySubject.getOrDefault(subjectName, List.of()).stream()
                    .sorted(Comparator.comparing(StudentAbsence::absenceDate, Comparator.reverseOrder()).thenComparing(StudentAbsence::id, Comparator.reverseOrder()))
                    .toList();
            int minimumGrades = planEntry.getValue() + 1;
            Double average = subjectGrades.size() >= minimumGrades
                    ? subjectGrades.stream().mapToInt(StudentGrade::gradeValue).average().orElse(0)
                    : null;

            List<String> teacherNames = teacherAssignment != null
                    ? List.of(teacherAssignment.teacherName())
                    : subjectGrades.stream().map(StudentGrade::teacherName).distinct().toList();

            subjectRows.add(new CatalogSubjectResponse(
                    subjectId,
                    subjectName,
                    planEntry.getValue(),
                    minimumGrades,
                    average == null ? null : Math.round(average * 100.0) / 100.0,
                    teacherNames,
                    subjectGrades.stream().map(grade -> gradeResponse(grade, requesterUsername, roles)).toList(),
                    subjectAbsences.stream().map(absence -> absenceResponse(absence, requesterUsername, roles)).toList(),
                    canAddGrade(requesterUsername, roles, student.classId(), subjectId, teacherAssignment)
            ));
        }

        return new CatalogResponse(
                toProfileResponse(student),
                subjectRows,
                hasRole(roles, "secretariat") || hasRole(roles, "professor")
        );
    }

    private GradeResponse gradeResponse(StudentGrade grade, String requesterUsername, List<String> roles) {
        return new GradeResponse(
                grade.id(),
                grade.studentUsername(),
                grade.studentName(),
                grade.classId(),
                grade.className(),
                grade.subjectId(),
                grade.subjectName(),
                grade.gradeValue(),
                grade.gradeDate(),
                grade.teacherUsername(),
                grade.teacherName(),
                grade.version(),
                canEditGrade(requesterUsername, roles, grade)
        );
    }

    private AbsenceResponse absenceResponse(StudentAbsence absence, String requesterUsername, List<String> roles) {
        return new AbsenceResponse(
                absence.id(),
                absence.studentUsername(),
                absence.studentName(),
                absence.classId(),
                absence.className(),
                absence.subjectId(),
                absence.subjectName(),
                absence.absenceDate(),
                absence.teacherUsername(),
                absence.teacherName(),
                absence.motivated(),
                absence.motivatedByUsername(),
                absence.motivatedByName(),
                absence.motivatedAt(),
                absence.version(),
                canMotivateAbsence(requesterUsername, roles, absence) && !absence.motivated()
        );
    }

    private boolean canAccessStudentCatalog(String requesterUsername, List<String> roles, UserProfile student) {
        if (hasRole(roles, "student")) {
            return requesterUsername.equals(student.username());
        }
        if (hasRole(roles, "professor")) {
            return student.classId() != null && classesForProfessor(requesterUsername).contains(student.classId());
        }
        return hasRole(roles, "secretariat") || hasRole(roles, "admin") || hasRole(roles, "sysadmin");
    }

    private boolean canViewGrade(String requesterUsername, List<String> roles, StudentGrade grade) {
        if (hasRole(roles, "student")) {
            return requesterUsername.equals(grade.studentUsername());
        }
        if (hasRole(roles, "professor")) {
            return professorCanManageGrade(requesterUsername, grade.classId(), grade.subjectId(), grade.teacherUsername());
        }
        return hasRole(roles, "secretariat") || hasRole(roles, "admin") || hasRole(roles, "sysadmin");
    }

    private boolean canViewAbsence(String requesterUsername, List<String> roles, StudentAbsence absence) {
        if (hasRole(roles, "student")) {
            return requesterUsername.equals(absence.studentUsername());
        }
        if (hasRole(roles, "professor")) {
            return classesForProfessor(requesterUsername).contains(absence.classId());
        }
        return hasRole(roles, "secretariat") || hasRole(roles, "admin") || hasRole(roles, "sysadmin");
    }

    private boolean canEditGrade(String requesterUsername, List<String> roles, StudentGrade grade) {
        if (hasRole(roles, "secretariat")) {
            return true;
        }
        return hasRole(roles, "professor")
                && professorCanManageGrade(requesterUsername, grade.classId(), grade.subjectId(), grade.teacherUsername());
    }

    private boolean canAddGrade(String requesterUsername, List<String> roles, Long classId, Long subjectId, TimetableEntry teacherAssignment) {
        if (teacherAssignment == null) {
            return false;
        }
        if (hasRole(roles, "secretariat")) {
            return true;
        }
        return hasRole(roles, "professor")
                && professorCanManageGrade(requesterUsername, classId, subjectId, teacherAssignment.teacherUsername());
    }

    private boolean canAddAbsence(String requesterUsername, List<String> roles, Long classId, Long subjectId, TimetableEntry teacherAssignment) {
        if (teacherAssignment == null) {
            return false;
        }
        if (hasRole(roles, "secretariat") || hasRole(roles, "admin") || hasRole(roles, "sysadmin")) {
            return true;
        }
        return hasRole(roles, "professor")
                && professorCanManageGrade(requesterUsername, classId, subjectId, teacherAssignment.teacherUsername());
    }

    private boolean canMotivateAbsence(String requesterUsername, List<String> roles, StudentAbsence absence) {
        if (hasRole(roles, "secretariat") || hasRole(roles, "admin") || hasRole(roles, "sysadmin")) {
            return true;
        }
        if (!hasRole(roles, "professor")) {
            return false;
        }
        return requesterUsername.equals(absence.teacherUsername())
                || isHomeroomTeacherForClass(requesterUsername, absence.classId());
    }

    private boolean professorCanManageGrade(String requesterUsername, Long classId, Long subjectId, String assignedTeacherUsername) {
        UserProfile professor = schoolDataService.getProfile(requesterUsername);
        String assignedSubjectName = schoolDataService.getSubjects().stream()
                .filter(subject -> Objects.equals(subject.id(), subjectId))
                .map(subject -> subject.name())
                .findFirst()
                .orElse(null);
        return Objects.equals(requesterUsername, assignedTeacherUsername)
                && assignedSubjectName != null
                && professor.subjectsTaught().contains(assignedSubjectName)
                && classId != null
                && teachesSubjectForClass(requesterUsername, classId, subjectId);
    }

    private List<UserProfile> getStudentsForProfessor(String professorUsername) {
        Set<Long> classIds = classesForProfessor(professorUsername);
        return schoolDataService.getUserProfilesByRole("student").stream()
                .filter(profile -> profile.classId() != null && classIds.contains(profile.classId()))
                .toList();
    }

    private Set<Long> classesForProfessor(String professorUsername) {
        return schoolDataService.getClasses().stream()
                .map(SchoolClass::id)
                .filter(classId -> schoolDataService.getTimetableForClass(classId).stream().anyMatch(entry -> professorUsername.equals(entry.teacherUsername())))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private boolean isHomeroomTeacherForClass(String professorUsername, Long classId) {
        SchoolClass schoolClass = schoolDataService.getClassById(classId);
        return Objects.equals(professorUsername, schoolClass.homeroomTeacherUsername());
    }

    private boolean teachesSubjectForClass(String professorUsername, Long classId, Long subjectId) {
        return schoolDataService.getTimetableForClass(classId).stream()
                .anyMatch(entry -> professorUsername.equals(entry.teacherUsername()) && Objects.equals(entry.subjectId(), subjectId));
    }

    private TimetableEntry assignedTeacherForClassSubject(Long classId, Long subjectId) {
        return schoolDataService.getTimetableForClass(classId).stream()
                .filter(entry -> Objects.equals(entry.subjectId(), subjectId))
                .findFirst()
                .orElse(null);
    }

    private void ensureCatalogVisible(List<String> roles) {
        if (!(hasRole(roles, "student")
                || hasRole(roles, "professor")
                || hasRole(roles, "secretariat")
                || hasRole(roles, "admin")
                || hasRole(roles, "sysadmin"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access the catalog");
        }
    }

    private boolean hasRole(List<String> roles, String role) {
        return roles.stream().anyMatch(role::equals);
    }

    private UserProfile requireStudentProfile(String username) {
        UserProfile profile = schoolDataService.getProfile(username);
        if (!"student".equals(profile.role())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
        return profile;
    }

    private ProfileResponse toProfileResponse(UserProfile profile) {
        SchoolClass schoolClass = profile.classId() == null ? null : schoolDataService.getClassById(profile.classId());
        return new ProfileResponse(
                profile.id(),
                profile.version(),
                profile.username(),
                profile.role(),
                profile.firstName(),
                profile.lastName(),
                profile.email(),
                null,
                null,
                null,
                null,
                null,
                profile.classId(),
                profile.className(),
                schoolClass == null ? null : schoolClass.profile(),
                profile.subjectsTaught(),
                null,
                null
        );
    }

    private void synchronizeStudentGrades(UserProfile updatedProfile) {
        List<StudentGrade> grades = gradesByStudentUsername.get(updatedProfile.username());
        if (grades == null || grades.isEmpty()) {
            return;
        }

        String updatedStudentName = updatedProfile.firstName() + " " + updatedProfile.lastName();
        for (int index = 0; index < grades.size(); index++) {
            StudentGrade grade = grades.get(index);
            StudentGrade updatedGrade = new StudentGrade(
                    grade.id(),
                    grade.studentUsername(),
                    updatedStudentName,
                    updatedProfile.classId(),
                    updatedProfile.className(),
                    grade.subjectId(),
                    grade.subjectName(),
                    grade.gradeValue(),
                    grade.gradeDate(),
                    grade.teacherUsername(),
                    grade.teacherName(),
                    grade.version()
            );
            grades.set(index, updatedGrade);
            persistentStateService.saveGrade(updatedGrade);
        }
    }

    private void synchronizeTeacherGrades(UserProfile updatedProfile) {
        String updatedTeacherName = updatedProfile.firstName() + " " + updatedProfile.lastName();
        for (List<StudentGrade> grades : gradesByStudentUsername.values()) {
            for (int index = 0; index < grades.size(); index++) {
                StudentGrade grade = grades.get(index);
                if (!updatedProfile.username().equals(grade.teacherUsername())) {
                    continue;
                }
                StudentGrade updatedGrade = new StudentGrade(
                        grade.id(),
                        grade.studentUsername(),
                        grade.studentName(),
                        grade.classId(),
                        grade.className(),
                        grade.subjectId(),
                        grade.subjectName(),
                        grade.gradeValue(),
                        grade.gradeDate(),
                        grade.teacherUsername(),
                        updatedTeacherName,
                        grade.version()
                );
                grades.set(index, updatedGrade);
                persistentStateService.saveGrade(updatedGrade);
            }
        }
    }

    private void synchronizeStudentAbsences(UserProfile updatedProfile) {
        List<StudentAbsence> absences = absencesByStudentUsername.get(updatedProfile.username());
        if (absences == null || absences.isEmpty()) {
            return;
        }

        String updatedStudentName = updatedProfile.firstName() + " " + updatedProfile.lastName();
        for (int index = 0; index < absences.size(); index++) {
            StudentAbsence absence = absences.get(index);
            StudentAbsence updatedAbsence = new StudentAbsence(
                    absence.id(),
                    absence.studentUsername(),
                    updatedStudentName,
                    updatedProfile.classId(),
                    updatedProfile.className(),
                    absence.subjectId(),
                    absence.subjectName(),
                    absence.absenceDate(),
                    absence.teacherUsername(),
                    absence.teacherName(),
                    absence.motivated(),
                    absence.motivatedByUsername(),
                    absence.motivatedByName(),
                    absence.motivatedAt(),
                    absence.version()
            );
            absences.set(index, updatedAbsence);
            persistentStateService.saveAbsence(updatedAbsence);
        }
    }

    private void synchronizeTeacherAbsences(UserProfile updatedProfile) {
        String updatedTeacherName = updatedProfile.firstName() + " " + updatedProfile.lastName();
        for (List<StudentAbsence> absences : absencesByStudentUsername.values()) {
            for (int index = 0; index < absences.size(); index++) {
                StudentAbsence absence = absences.get(index);
                String motivatedByName = absence.motivatedByUsername() != null && absence.motivatedByUsername().equals(updatedProfile.username())
                        ? updatedTeacherName
                        : absence.motivatedByName();
                if (!updatedProfile.username().equals(absence.teacherUsername()) && !updatedProfile.username().equals(absence.motivatedByUsername())) {
                    continue;
                }

                StudentAbsence updatedAbsence = new StudentAbsence(
                        absence.id(),
                        absence.studentUsername(),
                        absence.studentName(),
                        absence.classId(),
                        absence.className(),
                        absence.subjectId(),
                        absence.subjectName(),
                        absence.absenceDate(),
                        absence.teacherUsername(),
                        updatedProfile.username().equals(absence.teacherUsername()) ? updatedTeacherName : absence.teacherName(),
                        absence.motivated(),
                        absence.motivatedByUsername(),
                        motivatedByName,
                        absence.motivatedAt(),
                        absence.version()
                );
                absences.set(index, updatedAbsence);
                persistentStateService.saveAbsence(updatedAbsence);
            }
        }
    }

    private void loadPersistedGrades() {
        gradesByStudentUsername.clear();
        gradeIds.set(9000);

        for (StudentGrade grade : persistentStateService.loadGrades()) {
            gradesByStudentUsername.computeIfAbsent(grade.studentUsername(), ignored -> new ArrayList<>()).add(grade);
            gradeIds.set(Math.max(gradeIds.get(), grade.id()));
        }

        gradesByStudentUsername.values().forEach(this::sortGrades);
    }

    private void loadPersistedAbsences() {
        absencesByStudentUsername.clear();
        absenceIds.set(12000);

        for (StudentAbsence absence : persistentStateService.loadAbsences()) {
            absencesByStudentUsername.computeIfAbsent(absence.studentUsername(), ignored -> new ArrayList<>()).add(absence);
            absenceIds.set(Math.max(absenceIds.get(), absence.id()));
        }

        absencesByStudentUsername.values().forEach(this::sortAbsences);
    }

    private void seedCatalogDataIfNeeded() {
        List<UserProfile> students = schoolDataService.getUserProfilesByRole("student");
        if (students.isEmpty()) {
            return;
        }

        boolean shouldSeedGrades = totalGradeCount() < students.size() * 12;
        boolean shouldSeedAbsences = totalAbsenceCount() < students.size() * 3;
        if (!shouldSeedGrades && !shouldSeedAbsences) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate schoolYearStart = currentSchoolYearStart(today);
        List<StudentGrade> generatedGrades = new ArrayList<>();
        List<StudentAbsence> generatedAbsences = new ArrayList<>();

        for (UserProfile student : students) {
            if (student.classId() == null) {
                continue;
            }

            SchoolClass schoolClass = schoolDataService.getClassById(student.classId());
            LinkedHashMap<String, Integer> studyPlan = curriculumPlanService.hoursForClass(schoolClass.name(), schoolClass.profile());
            Map<String, List<StudentGrade>> existingGradesBySubject = gradesBySubject(student.username());
            Map<String, List<StudentAbsence>> existingAbsencesBySubject = absencesBySubject(student.username());

            for (Map.Entry<String, Integer> subjectEntry : studyPlan.entrySet()) {
                String subjectName = subjectEntry.getKey();
                int weeklyHours = subjectEntry.getValue() == null ? 0 : subjectEntry.getValue();
                Long subjectId = schoolDataService.subjectIdByName(subjectName);
                SeedTeacher teacher = resolveSeedTeacher(student.classId(), subjectId, subjectName);
                if (teacher == null) {
                    continue;
                }

                Random random = new Random(seedFor(student.username(), subjectName));

                if (shouldSeedGrades) {
                    List<StudentGrade> subjectGrades = existingGradesBySubject.getOrDefault(subjectName, List.of());
                    int targetGradeCount = targetGradeCount(weeklyHours, random);
                    if (subjectGrades.size() < targetGradeCount) {
                        List<StudentGrade> missingGrades = generateSeedGrades(
                                student,
                                subjectId,
                                subjectName,
                                teacher,
                                schoolYearStart,
                                today,
                                targetGradeCount - subjectGrades.size(),
                                weeklyHours,
                                random
                        );
                        generatedGrades.addAll(missingGrades);
                        existingGradesBySubject.computeIfAbsent(subjectName, ignored -> new ArrayList<>()).addAll(missingGrades);
                    }
                }

                if (shouldSeedAbsences) {
                    List<StudentAbsence> subjectAbsences = existingAbsencesBySubject.getOrDefault(subjectName, List.of());
                    int targetAbsenceCount = targetAbsenceCount(weeklyHours, random);
                    if (subjectAbsences.size() < targetAbsenceCount) {
                        List<StudentAbsence> missingAbsences = generateSeedAbsences(
                                student,
                                schoolClass,
                                subjectId,
                                subjectName,
                                teacher,
                                schoolYearStart,
                                today,
                                targetAbsenceCount - subjectAbsences.size(),
                                random,
                                subjectAbsences
                        );
                        generatedAbsences.addAll(missingAbsences);
                        existingAbsencesBySubject.computeIfAbsent(subjectName, ignored -> new ArrayList<>()).addAll(missingAbsences);
                    }
                }
            }
        }

        if (!generatedGrades.isEmpty()) {
            generatedGrades.forEach(grade -> gradesByStudentUsername
                    .computeIfAbsent(grade.studentUsername(), ignored -> new ArrayList<>())
                    .add(grade));
            gradesByStudentUsername.values().forEach(this::sortGrades);
            persistentStateService.saveGrades(generatedGrades);
        }

        if (!generatedAbsences.isEmpty()) {
            generatedAbsences.forEach(absence -> absencesByStudentUsername
                    .computeIfAbsent(absence.studentUsername(), ignored -> new ArrayList<>())
                    .add(absence));
            absencesByStudentUsername.values().forEach(this::sortAbsences);
            persistentStateService.saveAbsences(generatedAbsences);
        }
    }

    private int totalGradeCount() {
        return gradesByStudentUsername.values().stream().mapToInt(List::size).sum();
    }

    private int totalAbsenceCount() {
        return absencesByStudentUsername.values().stream().mapToInt(List::size).sum();
    }

    private Map<String, List<StudentGrade>> gradesBySubject(String studentUsername) {
        Map<String, List<StudentGrade>> bySubject = new LinkedHashMap<>();
        for (StudentGrade grade : gradesByStudentUsername.getOrDefault(studentUsername, List.of())) {
            bySubject.computeIfAbsent(grade.subjectName(), ignored -> new ArrayList<>()).add(grade);
        }
        return bySubject;
    }

    private Map<String, List<StudentAbsence>> absencesBySubject(String studentUsername) {
        Map<String, List<StudentAbsence>> bySubject = new LinkedHashMap<>();
        for (StudentAbsence absence : absencesByStudentUsername.getOrDefault(studentUsername, List.of())) {
            bySubject.computeIfAbsent(absence.subjectName(), ignored -> new ArrayList<>()).add(absence);
        }
        return bySubject;
    }

    private long seedFor(String studentUsername, String subjectName) {
        long result = CATALOG_SEED;
        result = 31 * result + studentUsername.hashCode();
        result = 31 * result + subjectName.hashCode();
        return result;
    }

    private LocalDate currentSchoolYearStart(LocalDate today) {
        int year = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        return LocalDate.of(year, 9, 15);
    }

    private SeedTeacher resolveSeedTeacher(Long classId, Long subjectId, String subjectName) {
        TimetableEntry assignment = assignedTeacherForClassSubject(classId, subjectId);
        if (assignment != null) {
            return new SeedTeacher(assignment.teacherUsername(), assignment.teacherName());
        }

        return schoolDataService.getUserProfilesByRole("professor").stream()
                .filter(profile -> profile.subjectsTaught() != null && profile.subjectsTaught().contains(subjectName))
                .findFirst()
                .map(profile -> new SeedTeacher(profile.username(), profile.firstName() + " " + profile.lastName()))
                .orElse(null);
    }

    private int targetGradeCount(int weeklyHours, Random random) {
        int minimumForAverage = Math.max(2, weeklyHours + 1);
        int extra = random.nextDouble() < 0.35 ? 1 : 0;
        return minimumForAverage + extra;
    }

    private int targetAbsenceCount(int weeklyHours, Random random) {
        if (random.nextDouble() < 0.22) {
            return 0;
        }
        int base = weeklyHours >= 3 ? 2 : 1;
        return base + (random.nextDouble() < 0.30 ? 1 : 0);
    }

    private List<StudentGrade> generateSeedGrades(
            UserProfile student,
            Long subjectId,
            String subjectName,
            SeedTeacher teacher,
            LocalDate schoolYearStart,
            LocalDate today,
            int count,
            int weeklyHours,
            Random random
    ) {
        List<StudentGrade> grades = new ArrayList<>();
        int spanDays = Math.max(45, (int) Math.max(1, ChronoUnit.DAYS.between(schoolYearStart, today)));
        for (int index = 0; index < count; index++) {
            int dayOffset = Math.min(spanDays, 12 + index * 18 + random.nextInt(18));
            LocalDate gradeDate = schoolYearStart.plusDays(dayOffset);
            if (gradeDate.isAfter(today)) {
                gradeDate = today.minusDays(random.nextInt(5));
            }

            grades.add(new StudentGrade(
                    gradeIds.incrementAndGet(),
                    student.username(),
                    student.firstName() + " " + student.lastName(),
                    student.classId(),
                    student.className(),
                    subjectId,
                    subjectName,
                    seededGradeValue(random, weeklyHours),
                    gradeDate.toString(),
                    teacher.username(),
                    teacher.fullName(),
                    1
            ));
        }
        return grades;
    }

    private int seededGradeValue(Random random, int weeklyHours) {
        int roll = random.nextInt(100);
        if (roll < 8) return 10;
        if (roll < 24) return 9;
        if (roll < 48) return 8;
        if (roll < 70) return 7;
        if (roll < 86) return 6;
        if (roll < 95) return 5;
        return weeklyHours >= 3 ? 4 : 5;
    }

    private List<StudentAbsence> generateSeedAbsences(
            UserProfile student,
            SchoolClass schoolClass,
            Long subjectId,
            String subjectName,
            SeedTeacher teacher,
            LocalDate schoolYearStart,
            LocalDate today,
            int count,
            Random random,
            List<StudentAbsence> existingAbsences
    ) {
        Set<String> usedDates = existingAbsences.stream()
                .map(StudentAbsence::absenceDate)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        List<StudentAbsence> absences = new ArrayList<>();
        int spanDays = Math.max(45, (int) Math.max(1, ChronoUnit.DAYS.between(schoolYearStart, today)));
        for (int index = 0; index < count; index++) {
            LocalDate absenceDate = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                int dayOffset = Math.min(spanDays, 10 + index * 14 + random.nextInt(24));
                LocalDate candidate = schoolYearStart.plusDays(dayOffset);
                if (candidate.isAfter(today)) {
                    candidate = today.minusDays(random.nextInt(7));
                }
                if (usedDates.add(candidate.toString())) {
                    absenceDate = candidate;
                    break;
                }
            }
            if (absenceDate == null) {
                continue;
            }

            boolean motivated = random.nextDouble() < 0.42;
            String motivatedByUsername = null;
            String motivatedByName = null;
            String motivatedAt = null;
            if (motivated) {
                if (schoolClass.homeroomTeacherUsername() != null && random.nextDouble() < 0.55) {
                    motivatedByUsername = schoolClass.homeroomTeacherUsername();
                    motivatedByName = schoolClass.homeroomTeacherName();
                } else {
                    motivatedByUsername = teacher.username();
                    motivatedByName = teacher.fullName();
                }
                motivatedAt = absenceDate.plusDays(Math.min(9, 1 + random.nextInt(7))).toString();
                if (LocalDate.parse(motivatedAt).isAfter(today)) {
                    motivatedAt = today.toString();
                }
            }

            absences.add(new StudentAbsence(
                    absenceIds.incrementAndGet(),
                    student.username(),
                    student.firstName() + " " + student.lastName(),
                    student.classId(),
                    student.className(),
                    subjectId,
                    subjectName,
                    absenceDate.toString(),
                    teacher.username(),
                    teacher.fullName(),
                    motivated,
                    motivatedByUsername,
                    motivatedByName,
                    motivatedAt,
                    1
            ));
        }
        return absences;
    }

    private void sortGrades(List<StudentGrade> grades) {
        grades.sort(Comparator.comparing(StudentGrade::subjectName).thenComparing(StudentGrade::gradeDate, Comparator.reverseOrder()).thenComparing(StudentGrade::id, Comparator.reverseOrder()));
    }

    private void sortAbsences(List<StudentAbsence> absences) {
        absences.sort(Comparator.comparing(StudentAbsence::subjectName).thenComparing(StudentAbsence::absenceDate, Comparator.reverseOrder()).thenComparing(StudentAbsence::id, Comparator.reverseOrder()));
    }

    private void ensureAbsenceNotDuplicated(String studentUsername, Long subjectId, String absenceDate) {
        boolean duplicate = absencesByStudentUsername.getOrDefault(studentUsername, List.of()).stream()
                .anyMatch(absence -> Objects.equals(absence.subjectId(), subjectId) && Objects.equals(absence.absenceDate(), absenceDate));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exista deja o absenta la aceasta materie pentru data selectata");
        }
    }

    private void ensureValidGradeValue(Integer gradeValue) {
        if (gradeValue == null || gradeValue < 1 || gradeValue > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nota invalida");
        }
    }
}
