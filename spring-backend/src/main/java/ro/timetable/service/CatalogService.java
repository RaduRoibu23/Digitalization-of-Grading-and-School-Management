package ro.timetable.service;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.model.SchoolClass;
import ro.timetable.model.StudentGrade;
import ro.timetable.model.TimetableEntry;
import ro.timetable.model.UserProfile;

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
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private final SchoolDataService schoolDataService;
    private final Map<String, List<StudentGrade>> gradesByStudentUsername = new LinkedHashMap<>();
    private final AtomicLong gradeIds = new AtomicLong(9000);

    public CatalogService(SchoolDataService schoolDataService) {
        this.schoolDataService = schoolDataService;
    }

    @PostConstruct
    void init() {
        seedGrades();
    }

    public List<Map<String, Object>> getCatalogStudents(String requesterUsername, List<String> roles) {
        ensureCatalogVisible(roles);

        List<UserProfile> students;
        if (hasRole(roles, "student")) {
            students = List.of(requireStudentProfile(requesterUsername));
        } else if (hasRole(roles, "professor")) {
            students = getStudentsForProfessor(requesterUsername);
        } else {
            students = schoolDataService.getProfilesByRole("student").stream()
                    .map(this::mapToStudentProfile)
                    .toList();
        }

        return students.stream()
                .sorted(Comparator.comparing(UserProfile::className, Comparator.nullsLast(String::compareTo))
                        .thenComparing(UserProfile::lastName)
                        .thenComparing(UserProfile::firstName)
                        .thenComparing(UserProfile::username))
                .map(this::profileResponse)
                .toList();
    }

    public Map<String, Object> getMyCatalog(String requesterUsername, List<String> roles) {
        ensureCatalogVisible(roles);
        if (!hasRole(roles, "student")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can access their own catalog endpoint");
        }
        return buildCatalogResponse(requireStudentProfile(requesterUsername), requesterUsername, roles);
    }

    public Map<String, Object> getCatalogForStudent(String requesterUsername, List<String> roles, String studentUsername) {
        ensureCatalogVisible(roles);
        UserProfile student = requireStudentProfile(studentUsername);
        if (!canAccessStudentCatalog(requesterUsername, roles, student)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this catalog");
        }
        return buildCatalogResponse(student, requesterUsername, roles);
    }

    public Map<String, Object> updateGrade(String requesterUsername, List<String> roles, Long gradeId, Integer version, Integer gradeValue, String gradeDate) {
        if (!hasRole(roles, "secretariat") && !hasRole(roles, "professor")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only secretariat and professors can update grades");
        }

        LocalDate.parse(gradeDate);

        for (Map.Entry<String, List<StudentGrade>> bucket : gradesByStudentUsername.entrySet()) {
            List<StudentGrade> grades = bucket.getValue();
            for (int index = 0; index < grades.size(); index++) {
                StudentGrade existing = grades.get(index);
                if (!Objects.equals(existing.id(), gradeId)) {
                    continue;
                }
                if (!Objects.equals(existing.version(), version)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Grade version is outdated");
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
                return gradeResponse(updated, requesterUsername, roles);
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grade not found");
    }

    private Map<String, Object> buildCatalogResponse(UserProfile student, String requesterUsername, List<String> roles) {
        List<Map<String, Object>> grades = gradesByStudentUsername.getOrDefault(student.username(), List.of()).stream()
                .filter(grade -> canViewGrade(requesterUsername, roles, grade))
                .sorted(Comparator.comparing(StudentGrade::subjectName).thenComparing(StudentGrade::gradeDate, Comparator.reverseOrder()))
                .map(grade -> gradeResponse(grade, requesterUsername, roles))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("student", profileResponse(student));
        response.put("grades", grades);
        response.put("can_edit", hasRole(roles, "secretariat") || hasRole(roles, "professor"));
        return response;
    }

    private Map<String, Object> gradeResponse(StudentGrade grade, String requesterUsername, List<String> roles) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", grade.id());
        response.put("student_username", grade.studentUsername());
        response.put("student_name", grade.studentName());
        response.put("class_id", grade.classId());
        response.put("class_name", grade.className());
        response.put("subject_id", grade.subjectId());
        response.put("subject_name", grade.subjectName());
        response.put("grade_value", grade.gradeValue());
        response.put("grade_date", grade.gradeDate());
        response.put("teacher_username", grade.teacherUsername());
        response.put("teacher_name", grade.teacherName());
        response.put("version", grade.version());
        response.put("editable", canEditGrade(requesterUsername, roles, grade));
        return response;
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
            return professorCanManageGrade(requesterUsername, grade);
        }
        return hasRole(roles, "secretariat") || hasRole(roles, "admin") || hasRole(roles, "sysadmin");
    }

    private boolean canEditGrade(String requesterUsername, List<String> roles, StudentGrade grade) {
        if (hasRole(roles, "secretariat")) {
            return true;
        }
        return hasRole(roles, "professor") && professorCanManageGrade(requesterUsername, grade);
    }

    private boolean professorCanManageGrade(String requesterUsername, StudentGrade grade) {
        UserProfile professor = schoolDataService.getProfile(requesterUsername);
        return professor.subjectsTaught().contains(grade.subjectName())
                && grade.classId() != null
                && teachesSubjectForClass(requesterUsername, grade.classId(), grade.subjectId());
    }

    private List<UserProfile> getStudentsForProfessor(String professorUsername) {
        Set<Long> classIds = classesForProfessor(professorUsername);
        return schoolDataService.getProfilesByRole("student").stream()
                .map(this::mapToStudentProfile)
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

    private UserProfile mapToStudentProfile(Map<String, Object> source) {
        Long classId = source.get("class_id") instanceof Number number ? number.longValue() : null;
        List<String> subjectsTaught = source.get("subjects_taught") instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();

        return new UserProfile(
                source.get("id") instanceof Number number ? number.longValue() : null,
                String.valueOf(source.get("username")),
                String.valueOf(source.get("role")),
                String.valueOf(source.get("first_name")),
                String.valueOf(source.get("last_name")),
                String.valueOf(source.get("email")),
                classId,
                source.get("class_name") == null ? null : String.valueOf(source.get("class_name")),
                subjectsTaught
        );
    }

    private Map<String, Object> profileResponse(UserProfile profile) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", profile.id());
        response.put("username", profile.username());
        response.put("role", profile.role());
        response.put("first_name", profile.firstName());
        response.put("last_name", profile.lastName());
        response.put("email", profile.email());
        response.put("class_id", profile.classId());
        response.put("class_name", profile.className());
        response.put("subjects_taught", profile.subjectsTaught());
        return response;
    }

    private void seedGrades() {
        gradesByStudentUsername.clear();
        for (Map<String, Object> studentMap : schoolDataService.getProfilesByRole("student")) {
            UserProfile student = mapToStudentProfile(studentMap);
            gradesByStudentUsername.put(student.username(), buildGradesForStudent(student));
        }
    }

    private List<StudentGrade> buildGradesForStudent(UserProfile student) {
        List<TimetableEntry> timetable = schoolDataService.getTimetableForClass(student.classId());
        LinkedHashMap<Long, TimetableEntry> subjectsForClass = new LinkedHashMap<>();
        for (TimetableEntry entry : timetable) {
            subjectsForClass.putIfAbsent(entry.subjectId(), entry);
        }

        List<StudentGrade> grades = new ArrayList<>();
        int subjectIndex = 0;
        for (TimetableEntry subjectEntry : subjectsForClass.values()) {
            int gradeCount = subjectIndex < 4 ? 2 : 1;
            for (int occurrence = 0; occurrence < gradeCount; occurrence++) {
                int seed = Math.floorMod(Objects.hash(student.username(), subjectEntry.subjectId(), occurrence), 10_000);
                int gradeValue = 5 + (seed % 6);
                LocalDate gradeDate = LocalDate.of(2025, 9, 15)
                        .plusDays(seed % 90L)
                        .plusDays(subjectIndex * 4L)
                        .plusDays(occurrence * 11L);

                grades.add(new StudentGrade(
                        gradeIds.incrementAndGet(),
                        student.username(),
                        student.firstName() + " " + student.lastName(),
                        student.classId(),
                        student.className(),
                        subjectEntry.subjectId(),
                        subjectEntry.subjectName(),
                        gradeValue,
                        gradeDate.toString(),
                        subjectEntry.teacherUsername(),
                        subjectEntry.teacherName(),
                        1
                ));
            }
            subjectIndex++;
        }

        return grades.stream()
                .sorted(Comparator.comparing(StudentGrade::subjectName).thenComparing(StudentGrade::gradeDate, Comparator.reverseOrder()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
