package ro.timetable.service;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.model.SchoolClass;
import ro.timetable.model.StudentGrade;
import ro.timetable.model.TimetableEntry;
import ro.timetable.model.UserProfile;
import ro.timetable.web.dto.ApiDtos.ActionResponse;
import ro.timetable.web.dto.ApiDtos.CatalogResponse;
import ro.timetable.web.dto.ApiDtos.CatalogSubjectResponse;
import ro.timetable.web.dto.ApiDtos.GradeResponse;
import ro.timetable.web.dto.ApiDtos.ProfileResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CatalogService {

    private final SchoolDataService schoolDataService;
    private final CurriculumPlanService curriculumPlanService;
    private final PersistentStateService persistentStateService;
    private final NotificationService notificationService;
    private final Map<String, List<StudentGrade>> gradesByStudentUsername = new LinkedHashMap<>();
    private final AtomicLong gradeIds = new AtomicLong(9000);

    public CatalogService(
            SchoolDataService schoolDataService,
            CurriculumPlanService curriculumPlanService,
            PersistentStateService persistentStateService,
            NotificationService notificationService
    ) {
        this.schoolDataService = schoolDataService;
        this.curriculumPlanService = curriculumPlanService;
        this.persistentStateService = persistentStateService;
        this.notificationService = notificationService;
    }

    @PostConstruct
    void init() {
        loadPersistedGrades();
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
        notificationService.createNotifications(List.of(student.username()), "Ai primit nota " + gradeValue + " la materia " + subjectName + ".");
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
                return new ActionResponse("Grade deleted", gradeId, null);
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade not found");
    }

    private CatalogResponse buildCatalogResponse(UserProfile student, String requesterUsername, List<String> roles) {
        SchoolClass schoolClass = schoolDataService.getClassById(student.classId());
        LinkedHashMap<String, Integer> weeklyHours = curriculumPlanService.hoursForClass(schoolClass.name(), schoolClass.profile());
        LinkedHashMap<String, List<StudentGrade>> gradesBySubject = new LinkedHashMap<>();

        for (StudentGrade grade : gradesByStudentUsername.getOrDefault(student.username(), List.of())) {
            if (!canViewGrade(requesterUsername, roles, grade)) {
                continue;
            }
            gradesBySubject.computeIfAbsent(grade.subjectName(), ignored -> new ArrayList<>()).add(grade);
        }

        List<CatalogSubjectResponse> subjectRows = new ArrayList<>();
        for (Map.Entry<String, Integer> planEntry : weeklyHours.entrySet()) {
            String subjectName = planEntry.getKey();
            Long subjectId = schoolDataService.subjectIdByName(subjectName);
            TimetableEntry teacherAssignment = assignedTeacherForClassSubject(student.classId(), subjectId);
            List<StudentGrade> subjectGrades = gradesBySubject.getOrDefault(subjectName, List.of()).stream()
                    .sorted(Comparator.comparing(StudentGrade::gradeDate, Comparator.reverseOrder()).thenComparing(StudentGrade::id, Comparator.reverseOrder()))
                    .toList();
            int minimumGrades = planEntry.getValue() + 1;
            Double average = subjectGrades.size() >= minimumGrades
                    ? subjectGrades.stream().mapToInt(StudentGrade::gradeValue).average().orElse(0)
                    : null;

            List<String> teacherNames = subjectGrades.stream().map(StudentGrade::teacherName).distinct().toList();
            if (teacherNames.isEmpty() && teacherAssignment != null) {
                teacherNames = List.of(teacherAssignment.teacherName());
            }

            subjectRows.add(new CatalogSubjectResponse(
                    subjectId,
                    subjectName,
                    planEntry.getValue(),
                    minimumGrades,
                    average == null ? null : Math.round(average * 100.0) / 100.0,
                    teacherNames,
                    subjectGrades.stream().map(grade -> gradeResponse(grade, requesterUsername, roles)).toList(),
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
                profile.username(),
                profile.role(),
                profile.firstName(),
                profile.lastName(),
                profile.email(),
                profile.classId(),
                profile.className(),
                schoolClass == null ? null : schoolClass.profile(),
                profile.subjectsTaught()
        );
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

    private void sortGrades(List<StudentGrade> grades) {
        grades.sort(Comparator.comparing(StudentGrade::subjectName).thenComparing(StudentGrade::gradeDate, Comparator.reverseOrder()).thenComparing(StudentGrade::id, Comparator.reverseOrder()));
    }

    private void ensureValidGradeValue(Integer gradeValue) {
        if (gradeValue == null || gradeValue < 1 || gradeValue > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nota invalida");
        }
    }
}