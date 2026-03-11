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
public class SchoolDataService {

    private record TeacherSeed(String username, String firstName, String lastName, String subjectName) {
    }

    private static final int CLASS_COUNT = 10;
    private static final int STUDENTS_PER_CLASS = 20;
    private static final String[] CLASS_NAMES = {
            "IX A", "IX B", "IX C", "X A", "X B",
            "X C", "XI A", "XI B", "XII A", "XII B"
    };
    private static final String[] CLASS_PROFILES = {
            "Filologie", "Matematica-Informatica", "Matematica-Informatica Intensiv",
            "Filologie", "Matematica-Informatica", "Matematica-Informatica Intensiv",
            "Filologie", "Matematica-Informatica",
            "Filologie", "Matematica-Informatica"
    };
    private static final String[] FIRST_NAMES = {
            "Andrei", "Maria", "Vlad", "Elena", "Alex", "Ioana", "Mihai", "Daria", "Stefan", "Bianca",
            "David", "Teodora", "Rares", "Ana", "Matei", "Gabriela", "Paul", "Ilinca", "Robert", "Larisa",
            "Denis", "Patricia", "Sebastian", "Adina", "Cristian", "Miruna", "Eduard", "Sonia", "Tudor", "Mara",
            "Albert", "Claudia", "Ionut", "Nicoleta", "Victor", "Amalia", "George", "Diana", "Cosmin", "Sabina"
    };
    private static final String[] LAST_NAMES = {
            "Popescu", "Ionescu", "Georgescu", "Stan", "Dumitru", "Marin", "Toma", "Petrescu", "Diaconescu", "Moldovan",
            "Radu", "Stoica", "Enache", "Nistor", "Voicu", "Sandu", "Munteanu", "Ilie", "Barbu", "Preda",
            "Constantin", "Lazar", "Nedelcu", "Dragomir", "Serban", "Coman", "Neagu", "Manole", "Ene", "Pavel",
            "Oprea", "Tudor", "Florea", "Apostol", "Dobre", "Tudose", "Matei", "Mocanu", "Avram", "Rosu"
    };

    private final Map<Long, SchoolClass> classes = new LinkedHashMap<>();
    private final Map<Long, Subject> subjects = new LinkedHashMap<>();
    private final Map<Long, Room> rooms = new LinkedHashMap<>();
    private final Map<String, UserProfile> profilesByUsername = new LinkedHashMap<>();
    private final Map<Long, List<TimetableEntry>> timetablesByClassId = new LinkedHashMap<>();
    private final Map<Long, List<String>> teachersBySubjectId = new LinkedHashMap<>();
    private final Map<String, Long> subjectIdsByName = new LinkedHashMap<>();
    private final AtomicLong entryIds = new AtomicLong(1000);
    private final AtomicLong profileIds = new AtomicLong(1);
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
                .sorted(Comparator.comparing(UserProfile::className, Comparator.nullsLast(String::compareTo))
                        .thenComparing(UserProfile::lastName)
                        .thenComparing(UserProfile::firstName)
                        .thenComparing(UserProfile::username))
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
                String teacherUsername = teacherUsernameForSubject(existing.classId(), existing.weekday(), existing.indexInDay(), subject.id());
                UserProfile teacher = profilesByUsername.get(teacherUsername);

                TimetableEntry updated = new TimetableEntry(
                        existing.id(),
                        existing.classId(),
                        existing.className(),
                        subject.id(),
                        subject.name(),
                        nextRoomId,
                        nextRoomName,
                        teacher.username(),
                        teacher.firstName() + " " + teacher.lastName(),
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
        response.put("class_profile", profile.classId() == null ? null : requireClass(profile.classId()).profile());
        response.put("subjects_taught", profile.subjectsTaught());
        response.put("claims", claims);
        if (profile.classId() != null) {
            SchoolClass schoolClass = requireClass(profile.classId());
            response.put("class", Map.of("id", profile.classId(), "name", profile.className(), "profile", schoolClass.profile()));
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
        response.put("class_profile", profile.classId() == null ? null : requireClass(profile.classId()).profile());
        response.put("subjects_taught", profile.subjectsTaught());
        return response;
    }

    private List<TimetableEntry> buildGeneratedTimetable(SchoolClass schoolClass) {
        List<Long> subjectSequence = buildSubjectSequence(schoolClass.profile());
        List<TimetableEntry> entries = new ArrayList<>();
        int slotsPerDay = 7;

        for (int slot = 0; slot < subjectSequence.size(); slot++) {
            int weekday = slot / slotsPerDay;
            int indexInDay = (slot % slotsPerDay) + 1;
            Long subjectId = subjectSequence.get(slot);
            Subject subject = requireSubject(subjectId);
            Room room = roomForSubject(subject.name(), schoolClass.profile(), slot);
            String teacherUsername = teacherUsernameForSubject(schoolClass.id(), weekday, indexInDay, subjectId);
            UserProfile teacher = profilesByUsername.get(teacherUsername);
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
                    weekday,
                    indexInDay,
                    1
            ));
        }
        return entries;
    }

    private List<Long> buildSubjectSequence(String profile) {
        LinkedHashMap<Long, Integer> plan = curriculumForProfile(profile);
        List<Long> sequence = new ArrayList<>();
        int remaining = plan.values().stream().mapToInt(Integer::intValue).sum();
        int pass = 0;
        while (remaining > 0) {
            for (Map.Entry<Long, Integer> entry : plan.entrySet()) {
                if (entry.getValue() <= 0) {
                    continue;
                }
                if ((sequence.size() % 7) >= 5 && isHeavySubject(entry.getKey()) && pass % 2 == 0) {
                    continue;
                }
                sequence.add(entry.getKey());
                plan.put(entry.getKey(), entry.getValue() - 1);
                remaining--;
            }
            pass++;
        }
        return sequence;
    }

    private boolean isHeavySubject(Long subjectId) {
        String subject = requireSubject(subjectId).name();
        return List.of("Matematica", "Informatica", "Fizica", "Chimie").contains(subject);
    }

    private LinkedHashMap<Long, Integer> curriculumForProfile(String profile) {
        LinkedHashMap<Long, Integer> plan = new LinkedHashMap<>();
        if ("Filologie".equals(profile)) {
            plan.put(subjectIdsByName.get("Limba si literatura romana"), 5);
            plan.put(subjectIdsByName.get("Limba engleza"), 4);
            plan.put(subjectIdsByName.get("Istorie"), 3);
            plan.put(subjectIdsByName.get("Geografie"), 3);
            plan.put(subjectIdsByName.get("Matematica"), 3);
            plan.put(subjectIdsByName.get("Biologie"), 2);
            plan.put(subjectIdsByName.get("Chimie"), 2);
            plan.put(subjectIdsByName.get("Fizica"), 2);
            plan.put(subjectIdsByName.get("Educatie fizica"), 2);
            plan.put(subjectIdsByName.get("Limba franceza"), 2);
            plan.put(subjectIdsByName.get("Limba latina"), 2);
            plan.put(subjectIdsByName.get("Informatica"), 1);
            plan.put(subjectIdsByName.get("Logica si argumentare"), 1);
            return plan;
        }
        if ("Matematica-Informatica Intensiv".equals(profile)) {
            plan.put(subjectIdsByName.get("Matematica"), 5);
            plan.put(subjectIdsByName.get("Informatica"), 6);
            plan.put(subjectIdsByName.get("Limba si literatura romana"), 4);
            plan.put(subjectIdsByName.get("Limba engleza"), 3);
            plan.put(subjectIdsByName.get("Fizica"), 3);
            plan.put(subjectIdsByName.get("Chimie"), 2);
            plan.put(subjectIdsByName.get("Biologie"), 1);
            plan.put(subjectIdsByName.get("Istorie"), 1);
            plan.put(subjectIdsByName.get("Geografie"), 1);
            plan.put(subjectIdsByName.get("Educatie fizica"), 2);
            plan.put(subjectIdsByName.get("Limba franceza"), 1);
            return plan;
        }
        plan.put(subjectIdsByName.get("Matematica"), 5);
        plan.put(subjectIdsByName.get("Informatica"), 4);
        plan.put(subjectIdsByName.get("Limba si literatura romana"), 4);
        plan.put(subjectIdsByName.get("Limba engleza"), 3);
        plan.put(subjectIdsByName.get("Fizica"), 3);
        plan.put(subjectIdsByName.get("Biologie"), 2);
        plan.put(subjectIdsByName.get("Chimie"), 2);
        plan.put(subjectIdsByName.get("Istorie"), 1);
        plan.put(subjectIdsByName.get("Geografie"), 1);
        plan.put(subjectIdsByName.get("Educatie fizica"), 2);
        plan.put(subjectIdsByName.get("Limba franceza"), 1);
        plan.put(subjectIdsByName.get("Logica si argumentare"), 1);
        return plan;
    }

    private Room roomForSubject(String subjectName, String profile, int slotIndex) {
        if ("Educatie fizica".equals(subjectName)) {
            return requireRoom(10L);
        }
        if ("Chimie".equals(subjectName)) {
            return requireRoom(8L);
        }
        if ("Fizica".equals(subjectName)) {
            return requireRoom(7L);
        }
        if ("Biologie".equals(subjectName)) {
            return requireRoom(9L);
        }
        if ("Informatica".equals(subjectName)) {
            return "Matematica-Informatica Intensiv".equals(profile) ? requireRoom(5L) : requireRoom(4L);
        }
        if ("Limba si literatura romana".equals(subjectName) || "Istorie".equals(subjectName) || "Geografie".equals(subjectName)) {
            return requireRoom(1L + (slotIndex % 3));
        }
        return requireRoom(2L + (slotIndex % 4));
    }

    private String teacherUsernameForSubject(Long classId, int weekday, int indexInDay, Long subjectId) {
        List<String> teacherUsernames = teachersBySubjectId.get(subjectId);
        if (teacherUsernames == null || teacherUsernames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No teacher available for subject " + requireSubject(subjectId).name());
        }
        int offset = Math.floorMod(classId.intValue() + weekday + indexInDay - 2, teacherUsernames.size());
        return teacherUsernames.get(offset);
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
        for (int index = 0; index < CLASS_COUNT; index++) {
            long classId = index + 1L;
            classes.put(classId, new SchoolClass(classId, CLASS_NAMES[index], CLASS_PROFILES[index]));
        }
    }

    private void seedSubjects() {
        addSubject(1L, "Limba si literatura romana");
        addSubject(2L, "Matematica");
        addSubject(3L, "Informatica");
        addSubject(4L, "Limba engleza");
        addSubject(5L, "Istorie");
        addSubject(6L, "Geografie");
        addSubject(7L, "Biologie");
        addSubject(8L, "Fizica");
        addSubject(9L, "Chimie");
        addSubject(10L, "Educatie fizica");
        addSubject(11L, "Limba franceza");
        addSubject(12L, "Limba latina");
        addSubject(13L, "Logica si argumentare");
    }

    private void addSubject(Long id, String name) {
        subjects.put(id, new Subject(id, name));
        subjectIdsByName.put(name, id);
    }

    private void seedRooms() {
        rooms.put(1L, new Room(1L, "Sala 101", 30));
        rooms.put(2L, new Room(2L, "Sala 102", 30));
        rooms.put(3L, new Room(3L, "Sala 103", 30));
        rooms.put(4L, new Room(4L, "Laborator Info 1", 28));
        rooms.put(5L, new Room(5L, "Laborator Info 2", 28));
        rooms.put(6L, new Room(6L, "Cabinet Limbi Moderne", 24));
        rooms.put(7L, new Room(7L, "Laborator Fizica", 24));
        rooms.put(8L, new Room(8L, "Laborator Chimie", 24));
        rooms.put(9L, new Room(9L, "Laborator Biologie", 24));
        rooms.put(10L, new Room(10L, "Sala de sport", 60));
    }

    private void seedProfiles() {
        addStaffProfile("sysadmin01", "sysadmin", "Sysadmin", "User", "sysadmin01@timetable.local");
        addStaffProfile("admin01", "admin", "Radu", "Roibu", "admin01@timetable.local");
        addStaffProfile("secretariat01", "secretariat", "Alexandra", "Corcodel", "secretariat01@timetable.local");
        addStaffProfile("scheduler01", "scheduler", "Scheduler", "User", "scheduler01@timetable.local");

        List<TeacherSeed> teacherSeeds = List.of(
                new TeacherSeed("professor01", "Bogdan", "Mocanu", "Limba si literatura romana"),
                new TeacherSeed("romana02", "Irina", "Nedelcu", "Limba si literatura romana"),
                new TeacherSeed("romana03", "Mihaela", "Voicu", "Limba si literatura romana"),
                new TeacherSeed("mate01", "Marius", "Stoica", "Matematica"),
                new TeacherSeed("mate02", "Laura", "Preda", "Matematica"),
                new TeacherSeed("mate03", "Cosmin", "Tudor", "Matematica"),
                new TeacherSeed("info01", "Roxana", "Ionescu", "Informatica"),
                new TeacherSeed("info02", "Adrian", "Dobre", "Informatica"),
                new TeacherSeed("info03", "Silviu", "Marin", "Informatica"),
                new TeacherSeed("engleza01", "Paula", "Dragomir", "Limba engleza"),
                new TeacherSeed("engleza02", "Mirela", "Stan", "Limba engleza"),
                new TeacherSeed("engleza03", "Anca", "Lazar", "Limba engleza"),
                new TeacherSeed("istorie01", "Daniel", "Barbu", "Istorie"),
                new TeacherSeed("istorie02", "Oana", "Munteanu", "Istorie"),
                new TeacherSeed("geografie01", "Florin", "Serban", "Geografie"),
                new TeacherSeed("geografie02", "Raluca", "Ene", "Geografie"),
                new TeacherSeed("biologie01", "Monica", "Avram", "Biologie"),
                new TeacherSeed("biologie02", "Cristina", "Matei", "Biologie"),
                new TeacherSeed("fizica01", "Dorin", "Coman", "Fizica"),
                new TeacherSeed("fizica02", "Alexandru", "Neagu", "Fizica"),
                new TeacherSeed("chimie01", "Camelia", "Popescu", "Chimie"),
                new TeacherSeed("chimie02", "Simona", "Florea", "Chimie"),
                new TeacherSeed("sport01", "Lucian", "Radu", "Educatie fizica"),
                new TeacherSeed("sport02", "Carmen", "Pavel", "Educatie fizica"),
                new TeacherSeed("franceza01", "Cecilia", "Petrescu", "Limba franceza"),
                new TeacherSeed("franceza02", "Daniela", "Apostol", "Limba franceza"),
                new TeacherSeed("latina01", "Adela", "Toma", "Limba latina"),
                new TeacherSeed("logica01", "Sorin", "Georgescu", "Logica si argumentare")
        );

        for (TeacherSeed teacherSeed : teacherSeeds) {
            addTeacherProfile(teacherSeed);
        }

        for (int studentIndex = 1; studentIndex <= CLASS_COUNT * STUDENTS_PER_CLASS; studentIndex++) {
            long classId = ((studentIndex - 1) / STUDENTS_PER_CLASS) + 1L;
            String username = String.format("student%03d", studentIndex);
            String firstName = FIRST_NAMES[(studentIndex - 1) % FIRST_NAMES.length];
            String lastName = LAST_NAMES[((studentIndex - 1) / FIRST_NAMES.length + studentIndex - 1) % LAST_NAMES.length];
            profilesByUsername.put(username, new UserProfile(
                    profileIds.getAndIncrement(),
                    username,
                    "student",
                    firstName,
                    lastName,
                    username + "@timetable.local",
                    classId,
                    classes.get(classId).name(),
                    List.of()
            ));
        }
    }

    private void addStaffProfile(String username, String role, String firstName, String lastName, String email) {
        profilesByUsername.put(username, new UserProfile(profileIds.getAndIncrement(), username, role, firstName, lastName, email, null, null, List.of()));
    }

    private void addTeacherProfile(TeacherSeed teacherSeed) {
        Long subjectId = subjectIdsByName.get(teacherSeed.subjectName());
        if (subjectId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing subject for teacher seed " + teacherSeed.subjectName());
        }
        profilesByUsername.put(teacherSeed.username(), new UserProfile(
                profileIds.getAndIncrement(),
                teacherSeed.username(),
                "professor",
                teacherSeed.firstName(),
                teacherSeed.lastName(),
                teacherSeed.username() + "@timetable.local",
                null,
                null,
                List.of(teacherSeed.subjectName())
        ));
        teachersBySubjectId.computeIfAbsent(subjectId, ignored -> new ArrayList<>()).add(teacherSeed.username());
    }

    private void seedTimetables() {
        for (SchoolClass schoolClass : classes.values()) {
            timetablesByClassId.put(schoolClass.id(), buildGeneratedTimetable(schoolClass));
        }
    }
}
