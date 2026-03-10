package ro.timetable.service;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.model.Room;
import ro.timetable.model.SchoolClass;
import ro.timetable.model.Subject;
import ro.timetable.model.TimetableEntry;
import ro.timetable.model.UserProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class DemoSchoolService {

    private final Map<Long, SchoolClass> classes = new LinkedHashMap<>();
    private final Map<Long, Subject> subjects = new LinkedHashMap<>();
    private final Map<Long, Room> rooms = new LinkedHashMap<>();
    private final Map<String, UserProfile> profilesByUsername = new LinkedHashMap<>();
    private final Map<Long, List<TimetableEntry>> timetablesByClassId = new LinkedHashMap<>();
    private final AtomicLong entryIds = new AtomicLong(1000);
    private final AtomicLong jobIds = new AtomicLong(5000);

    @PostConstruct
    void init() {
        seedClasses();
        seedSubjects();
        seedRooms();
        seedProfiles();
        seedTimetables();
    }

    public List<SchoolClass> getClasses() {
        return new ArrayList<>(classes.values());
    }

    public List<Subject> getSubjects() {
        return new ArrayList<>(subjects.values());
    }

    public List<Room> getRooms() {
        return new ArrayList<>(rooms.values());
    }

    public UserProfile getProfile(String username) {
        UserProfile profile = profilesByUsername.get(username);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");
        }
        return profile;
    }

    public List<Map<String, Object>> getProfilesByRole(String role) {
        return profilesByUsername.values().stream()
                .filter(profile -> role == null || role.isBlank() || role.equalsIgnoreCase(profile.role()))
                .sorted(Comparator.comparing(UserProfile::lastName).thenComparing(UserProfile::firstName))
                .map(this::profileResponse)
                .collect(Collectors.toList());
    }

    public List<TimetableEntry> getTimetableForClass(Long classId) {
        requireClass(classId);
        return copyEntries(timetablesByClassId.getOrDefault(classId, List.of()));
    }

    public List<TimetableEntry> getTimetableForTeacher(String username) {
        return timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> username.equals(entry.teacherUsername()))
                .sorted(Comparator.comparing(TimetableEntry::weekday).thenComparing(TimetableEntry::indexInDay))
                .toList();
    }

    public Map<String, Object> generateTimetable(Long classId) {
        SchoolClass schoolClass = requireClass(classId);
        List<TimetableEntry> generated = buildGeneratedTimetable(schoolClass);
        timetablesByClassId.put(classId, generated);

        return Map.of(
                "detail", "Timetable generated",
                "job_ids", List.of(jobIds.incrementAndGet())
        );
    }

    public void deleteTimetable(Long classId) {
        requireClass(classId);
        timetablesByClassId.remove(classId);
    }

    public TimetableEntry updateEntry(Long entryId, Integer version, Long subjectId, Long roomId) {
        for (Map.Entry<Long, List<TimetableEntry>> bucket : timetablesByClassId.entrySet()) {
            List<TimetableEntry> entries = bucket.getValue();
            for (int i = 0; i < entries.size(); i++) {
                TimetableEntry existing = entries.get(i);
                if (!Objects.equals(existing.id(), entryId)) {
                    continue;
                }
                if (!Objects.equals(existing.version(), version)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Timetable entry version is outdated");
                }

                Subject subject = subjectId != null ? requireSubject(subjectId) : subjects.get(existing.subjectId());
                Room room = roomId != null ? requireRoom(roomId) : (existing.roomId() == null ? null : rooms.get(existing.roomId()));
                Long nextRoomId = room != null ? room.id() : null;
                String nextRoomName = room != null ? room.name() : null;

                TimetableEntry updated = new TimetableEntry(
                        existing.id(),
                        existing.classId(),
                        existing.className(),
                        subject.id(),
                        subject.name(),
                        nextRoomId,
                        nextRoomName,
                        existing.teacherUsername(),
                        existing.teacherName(),
                        existing.weekday(),
                        existing.indexInDay(),
                        existing.version() + 1
                );
                entries.set(i, updated);
                return updated;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timetable entry not found");
    }

    public Map<String, Object> meResponse(String username, List<String> roles, Map<String, Object> claims) {
        UserProfile profile = getProfile(username);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", profile.id());
        response.put("username", profile.username());
        response.put("first_name", profile.firstName());
        response.put("last_name", profile.lastName());
        response.put("email", profile.email());
        response.put("role", profile.role());
        response.put("roles", roles);
        response.put("class_id", profile.classId());
        response.put("class_name", profile.className());
        response.put("subjects_taught", profile.subjectsTaught());
        response.put("claims", claims);
        if (profile.classId() != null) {
            response.put("class", Map.of("id", profile.classId(), "name", profile.className()));
        }
        return response;
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

    private List<TimetableEntry> buildGeneratedTimetable(SchoolClass schoolClass) {
        List<Long> subjectIds = switch (schoolClass.id().intValue()) {
            case 1 -> List.of(1L, 2L, 3L, 4L, 5L);
            case 2 -> List.of(2L, 3L, 4L, 5L, 1L);
            default -> List.of(3L, 1L, 5L, 2L, 4L);
        };

        List<TimetableEntry> entries = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            Subject subject = requireSubject(subjectIds.get(day));
            Room room = requireRoom((long) ((day % rooms.size()) + 1));
            UserProfile teacher = teacherForSubject(subject.id());
            entries.add(new TimetableEntry(
                    entryIds.incrementAndGet(),
                    schoolClass.id(),
                    schoolClass.name(),
                    subject.id(),
                    subject.name(),
                    room.id(),
                    room.name(),
                    teacher.username(),
                    teacher.firstName() + " " + teacher.lastName(),
                    day,
                    day + 1,
                    1
            ));
        }
        return entries;
    }

    private List<TimetableEntry> copyEntries(List<TimetableEntry> source) {
        return source.stream()
                .sorted(Comparator.comparing(TimetableEntry::weekday).thenComparing(TimetableEntry::indexInDay))
                .map(entry -> new TimetableEntry(
                        entry.id(),
                        entry.classId(),
                        entry.className(),
                        entry.subjectId(),
                        entry.subjectName(),
                        entry.roomId(),
                        entry.roomName(),
                        entry.teacherUsername(),
                        entry.teacherName(),
                        entry.weekday(),
                        entry.indexInDay(),
                        entry.version()
                ))
                .collect(Collectors.toList());
    }

    private UserProfile teacherForSubject(Long subjectId) {
        return profilesByUsername.values().stream()
                .filter(profile -> "professor".equals(profile.role()))
                .filter(profile -> profile.subjectsTaught().stream().map(String::toLowerCase).toList().contains(requireSubject(subjectId).name().toLowerCase()))
                .findFirst()
                .orElseGet(() -> profilesByUsername.get("professor01"));
    }

    private SchoolClass requireClass(Long classId) {
        SchoolClass schoolClass = classes.get(classId);
        if (schoolClass == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found");
        }
        return schoolClass;
    }

    private Subject requireSubject(Long subjectId) {
        Subject subject = subjects.get(subjectId);
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found");
        }
        return subject;
    }

    private Room requireRoom(Long roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
        return room;
    }

    private void seedClasses() {
        classes.put(1L, new SchoolClass(1L, "Grupa 131"));
        classes.put(2L, new SchoolClass(2L, "Grupa 132"));
        classes.put(3L, new SchoolClass(3L, "Grupa 133"));
    }

    private void seedSubjects() {
        subjects.put(1L, new Subject(1L, "Programare Java"));
        subjects.put(2L, new Subject(2L, "Baze de date"));
        subjects.put(3L, new Subject(3L, "Inginerie software"));
        subjects.put(4L, new Subject(4L, "Retele de calculatoare"));
        subjects.put(5L, new Subject(5L, "Algoritmi avansati"));
    }

    private void seedRooms() {
        rooms.put(1L, new Room(1L, "C301", 30));
        rooms.put(2L, new Room(2L, "C302", 30));
        rooms.put(3L, new Room(3L, "Lab Info 1", 25));
        rooms.put(4L, new Room(4L, "Lab Info 2", 25));
        rooms.put(5L, new Room(5L, "Amfiteatru A1", 120));
    }

    private void seedProfiles() {
        profilesByUsername.put("sysadmin01", new UserProfile(1L, "sysadmin01", "sysadmin", "Sysadmin", "User", "sysadmin01@timetable.local", null, null, List.of()));
        profilesByUsername.put("admin01", new UserProfile(2L, "admin01", "admin", "Radu", "Roibu", "admin01@timetable.local", null, null, List.of()));
        profilesByUsername.put("secretariat01", new UserProfile(3L, "secretariat01", "secretariat", "Alexandra", "Corcodel", "secretariat01@timetable.local", null, null, List.of()));
        profilesByUsername.put("scheduler01", new UserProfile(4L, "scheduler01", "scheduler", "Scheduler", "User", "scheduler01@timetable.local", null, null, List.of()));
        profilesByUsername.put("professor01", new UserProfile(5L, "professor01", "professor", "Bogdan", "Mocanu", "professor01@timetable.local", null, null, List.of("Programare Java", "Inginerie software")));
        profilesByUsername.put("student01", new UserProfile(6L, "student01", "student", "Andrei", "Voicu", "student01@timetable.local", 1L, classes.get(1L).name(), List.of()));
        profilesByUsername.put("student02", new UserProfile(7L, "student02", "student", "Maria", "Ionescu", "student02@timetable.local", 1L, classes.get(1L).name(), List.of()));
        profilesByUsername.put("student03", new UserProfile(8L, "student03", "student", "Vlad", "Popescu", "student03@timetable.local", 2L, classes.get(2L).name(), List.of()));
        profilesByUsername.put("student04", new UserProfile(9L, "student04", "student", "Elena", "Dumitru", "student04@timetable.local", 3L, classes.get(3L).name(), List.of()));
    }

    private void seedTimetables() {
        timetablesByClassId.put(1L, buildGeneratedTimetable(classes.get(1L)));
        timetablesByClassId.put(2L, buildGeneratedTimetable(classes.get(2L)));
    }
}
