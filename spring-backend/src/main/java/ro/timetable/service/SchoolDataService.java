
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class SchoolDataService {

    private record TeacherSeed(String username, String firstName, String lastName, String subjectName) {
    }

    private record Slot(int weekday, int indexInDay) {
    }

    private record SlotAssignment(Slot slot, Long subjectId, String teacherUsername, Long roomId) {
    }

    private static final int CLASS_COUNT = 10;
    private static final int STUDENTS_PER_CLASS = 20;
    private static final int WEEK_DAYS = 5;
    private static final int SLOTS_PER_DAY = 7;
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

    private final CurriculumPlanService curriculumPlanService;
    private final PersistentStateService persistentStateService;
    private final NotificationService notificationService;
    private final Map<Long, SchoolClass> classes = new LinkedHashMap<>();
    private final Map<Long, Subject> subjects = new LinkedHashMap<>();
    private final Map<Long, Room> rooms = new LinkedHashMap<>();
    private final Map<String, UserProfile> profilesByUsername = new LinkedHashMap<>();
    private final Map<Long, List<TimetableEntry>> timetablesByClassId = new LinkedHashMap<>();
    private final Map<Long, List<String>> teachersBySubjectId = new LinkedHashMap<>();
    private final Map<String, Long> subjectIdsByName = new LinkedHashMap<>();
    private final Map<Long, Long> homeRoomIdsByClassId = new LinkedHashMap<>();
    private final AtomicLong entryIds = new AtomicLong(1000);
    private final AtomicLong profileIds = new AtomicLong(1);
    private final AtomicLong jobIds = new AtomicLong(5000);

    public SchoolDataService(CurriculumPlanService curriculumPlanService, PersistentStateService persistentStateService, NotificationService notificationService) {
        this.curriculumPlanService = curriculumPlanService;
        this.persistentStateService = persistentStateService;
        this.notificationService = notificationService;
    }

    @PostConstruct
    void init() {
        seedClasses();
        seedSubjects();
        seedRooms();
        seedProfiles();
        loadPersistedTimetables();
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

    public List<SchoolClass> getPublicClasses() {
        return getClasses();
    }

    public UserProfile getProfile(String username) {
        UserProfile profile = profilesByUsername.get(username);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");
        }
        return profile;
    }

    public boolean hasProfile(String username) {
        return profilesByUsername.containsKey(username);
    }

    public Map<String, Object> registerStudentProfile(String username, String firstName, String lastName, String email, Long classId) {
        if (hasProfile(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        SchoolClass schoolClass = requireClass(classId);
        UserProfile profile = new UserProfile(
                profileIds.getAndIncrement(),
                username,
                "student",
                firstName,
                lastName,
                email,
                classId,
                schoolClass.name(),
                List.of()
        );
        profilesByUsername.put(username, profile);
        return profileResponse(profile);
    }

    public SchoolClass getClassById(Long classId) {
        return requireClass(classId);
    }

    public List<String> getStudentUsernamesForClass(Long classId) {
        requireClass(classId);
        return profilesByUsername.values().stream()
                .filter(profile -> "student".equals(profile.role()))
                .filter(profile -> Objects.equals(classId, profile.classId()))
                .sorted(Comparator.comparing(UserProfile::username))
                .map(UserProfile::username)
                .toList();
    }


    public int weeklyHoursForSubject(Long classId, String subjectName) {
        SchoolClass schoolClass = requireClass(classId);
        return curriculumPlanService.weeklyHoursForSubject(schoolClass.name(), schoolClass.profile(), subjectName);
    }

    public Long subjectIdByName(String subjectName) {
        Long subjectId = subjectIdsByName.get(subjectName);
        if (subjectId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found");
        }
        return subjectId;
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
        List<TimetableEntry> generated = buildGeneratedTimetable(schoolClass, classId);
        timetablesByClassId.put(classId, generated);
        persistentStateService.replaceTimetableForClass(classId, generated);
        return Map.of(
                "detail", "Timetable generated",
                "job_ids", List.of(jobIds.incrementAndGet())
        );
    }

    public void deleteTimetable(Long classId) {
        requireClass(classId);
        timetablesByClassId.remove(classId);
        persistentStateService.deleteTimetable(classId);
    }

    public TimetableEntry updateEntry(Long entryId, Integer version, Long subjectId, Long roomId) {
        for (Map.Entry<Long, List<TimetableEntry>> bucket : timetablesByClassId.entrySet()) {
            List<TimetableEntry> entries = bucket.getValue();
            for (int index = 0; index < entries.size(); index++) {
                TimetableEntry existing = entries.get(index);
                if (!Objects.equals(existing.id(), entryId)) {
                    continue;
                }
                if (!Objects.equals(existing.version(), version)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Orarul a fost modificat intre timp. Da refresh si incearca din nou.");
                }

                Subject subject = subjectId != null ? requireSubject(subjectId) : requireSubject(existing.subjectId());
                Room room = roomId != null
                        ? requireRoom(roomId)
                        : defaultRoomForSubject(existing.classId(), subject.name(), existing.weekday(), existing.indexInDay(), existing.id());
                String assignedTeacherUsername = assignedTeacherForClassSubject(existing.classId(), subject.id(), existing.id());
                String teacherUsername;
                if (assignedTeacherUsername != null) {
                    teacherUsername = validateTeacherAvailability(assignedTeacherUsername, subject.id(), existing.id(), existing.weekday(), existing.indexInDay());
                } else if (Objects.equals(subject.id(), existing.subjectId())) {
                    teacherUsername = validateTeacherAvailability(existing.teacherUsername(), subject.id(), existing.id(), existing.weekday(), existing.indexInDay());
                } else {
                    teacherUsername = selectTeacherForSubject(subject.id(), existing.id(), existing.weekday(), existing.indexInDay());
                }
                validateRoomAvailability(room.id(), existing.id(), existing.weekday(), existing.indexInDay());
                UserProfile teacher = profilesByUsername.get(teacherUsername);

                if (Objects.equals(existing.subjectId(), subject.id())
                        && Objects.equals(existing.roomId(), room.id())
                        && Objects.equals(existing.teacherUsername(), teacher.username())) {
                    return existing;
                }

                TimetableEntry updated = new TimetableEntry(
                        existing.id(),
                        existing.classId(),
                        existing.className(),
                        subject.id(),
                        subject.name(),
                        room.id(),
                        room.name(),
                        teacher.username(),
                        teacher.firstName() + " " + teacher.lastName(),
                        existing.weekday(),
                        existing.indexInDay(),
                        existing.version() + 1
                );
                entries.set(index, updated);
                persistentStateService.saveTimetableEntry(updated);
                notifyStudentsAboutTimetableChange(updated);
                return updated;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timetable entry not found");
    }

    private void notifyStudentsAboutTimetableChange(TimetableEntry entry) {
        List<String> recipients = getStudentUsernamesForClass(entry.classId());
        if (recipients.isEmpty()) {
            return;
        }
        notificationService.createNotifications(recipients, buildTimetableChangeMessage(entry));
    }

    private String buildTimetableChangeMessage(TimetableEntry entry) {
        return "Orarul tau a fost modificat: "
                + weekdayLabel(entry.weekday())
                + ", "
                + slotTimeLabel(entry.indexInDay())
                + " - "
                + entry.subjectName()
                + " in sala "
                + entry.roomName()
                + ".";
    }

    private String weekdayLabel(Integer weekday) {
        return switch (weekday == null ? 0 : weekday) {
            case 1 -> "Luni";
            case 2 -> "Marti";
            case 3 -> "Miercuri";
            case 4 -> "Joi";
            case 5 -> "Vineri";
            default -> "Zi necunoscuta";
        };
    }

    private String slotTimeLabel(Integer indexInDay) {
        return switch (indexInDay == null ? 0 : indexInDay) {
            case 1 -> "08:00-08:50";
            case 2 -> "09:00-09:50";
            case 3 -> "10:00-10:50";
            case 4 -> "11:00-11:50";
            case 5 -> "12:00-12:50";
            case 6 -> "13:00-13:50";
            case 7 -> "14:00-14:50";
            default -> "interval necunoscut";
        };
    }
    private void loadPersistedTimetables() {
        timetablesByClassId.clear();
        entryIds.set(1000);

        for (TimetableEntry entry : persistentStateService.loadTimetableEntries()) {
            timetablesByClassId.computeIfAbsent(entry.classId(), ignored -> new ArrayList<>()).add(entry);
            entryIds.set(Math.max(entryIds.get(), entry.id()));
        }

        timetablesByClassId.values().forEach(entries -> entries.sort(Comparator.comparing(TimetableEntry::weekday).thenComparing(TimetableEntry::indexInDay)));
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
        response.put("subjects_taught", profile.subjectsTaught());
        if (profile.classId() != null) {
            response.put("class_profile", requireClass(profile.classId()).profile());
        }
        return response;
    }

    private List<TimetableEntry> buildGeneratedTimetable(SchoolClass schoolClass, Long classId) {
        List<SlotAssignment> assignments = buildAssignments(schoolClass, classId);
        List<TimetableEntry> generated = new ArrayList<>();
        for (SlotAssignment assignment : assignments) {
            Subject subject = requireSubject(assignment.subjectId());
            Room room = requireRoom(assignment.roomId());
            UserProfile teacher = getProfile(assignment.teacherUsername());
            generated.add(new TimetableEntry(
                    entryIds.incrementAndGet(),
                    classId,
                    schoolClass.name(),
                    subject.id(),
                    subject.name(),
                    room.id(),
                    room.name(),
                    teacher.username(),
                    teacher.firstName() + " " + teacher.lastName(),
                    assignment.slot().weekday(),
                    assignment.slot().indexInDay(),
                    1
            ));
        }
        generated.sort(Comparator.comparing(TimetableEntry::weekday).thenComparing(TimetableEntry::indexInDay));
        return generated;
    }

    private List<SlotAssignment> buildAssignments(SchoolClass schoolClass, Long classId) {
        LinkedHashMap<String, Integer> plan = timetablePlanForClass(schoolClass);
        List<String> baseOccurrences = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : plan.entrySet()) {
            for (int index = 0; index < entry.getValue(); index++) {
                baseOccurrences.add(entry.getKey());
            }
        }

        baseOccurrences.sort(Comparator.comparingInt((String subjectName) -> plan.getOrDefault(subjectName, 0)).reversed()
                .thenComparing(subjectName -> isHeavySubject(subjectName) ? 0 : 1)
                .thenComparing(String::compareTo));

        List<Slot> baseSlots = buildSlotsForClass(plan.values().stream().mapToInt(Integer::intValue).sum());
        ResponseStatusException lastFailure = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            List<String> occurrences = new ArrayList<>(baseOccurrences);
            List<Slot> slots = new ArrayList<>(baseSlots);
            if (attempt > 0) {
                Collections.shuffle(occurrences, new Random(classId * 97 + attempt));
                Collections.shuffle(slots, new Random(classId * 211 + attempt));
            }
            try {
                return tryBuildAssignments(schoolClass, classId, occurrences, slots);
            } catch (ResponseStatusException exception) {
                lastFailure = exception;
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Nu am putut genera un orar valid pentru " + schoolClass.name() + ".");
    }

    private List<SlotAssignment> tryBuildAssignments(SchoolClass schoolClass, Long classId, List<String> occurrences, List<Slot> slots) {
        Map<String, String> occupiedTeachers = occupiedTeachers(classId);
        Map<String, String> occupiedRooms = occupiedRooms(classId);
        Map<String, Integer> daySubjectCounts = new LinkedHashMap<>();
        Map<Long, String> teacherBySubjectId = new LinkedHashMap<>();
        List<SlotAssignment> assignments = new ArrayList<>();
        Set<String> usedSlots = new HashSet<>();

        for (String subjectName : occurrences) {
            Long subjectId = subjectIdsByName.get(subjectName);
            if (subjectId == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing subject " + subjectName);
            }
            String fixedTeacherUsername = teacherBySubjectId.get(subjectId);
            SlotAssignment best = pickBestAssignment(classId, subjectId, subjectName, fixedTeacherUsername, slots, assignments, usedSlots, daySubjectCounts, occupiedTeachers, occupiedRooms);
            if (best == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Nu am putut genera un orar valid pentru " + schoolClass.name() + ".");
            }
            teacherBySubjectId.putIfAbsent(subjectId, best.teacherUsername());
            assignments.add(best);
            usedSlots.add(slotKey(best.slot().weekday(), best.slot().indexInDay()));
            occupiedTeachers.put(slotKey(best.slot().weekday(), best.slot().indexInDay(), best.teacherUsername()), schoolClass.name());
            occupiedRooms.put(slotKey(best.slot().weekday(), best.slot().indexInDay(), best.roomId()), schoolClass.name());
            daySubjectCounts.merge(daySubjectKey(best.slot().weekday(), subjectName), 1, Integer::sum);
        }

        return assignments;
    }

    private SlotAssignment pickBestAssignment(Long classId, Long subjectId, String subjectName, String fixedTeacherUsername, List<Slot> slots,
                                              List<SlotAssignment> assignments, Set<String> usedSlots, Map<String, Integer> daySubjectCounts,
                                              Map<String, String> occupiedTeachers, Map<String, String> occupiedRooms) {
        List<String> candidateTeachers = teachersBySubjectId.getOrDefault(subjectId, List.of());
        if (candidateTeachers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No teachers configured for subject " + subjectName);
        }

        SlotAssignment best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            if (usedSlots.contains(slotKey(slot.weekday(), slot.indexInDay()))) {
                continue;
            }

            int sameDayCount = daySubjectCounts.getOrDefault(daySubjectKey(slot.weekday(), subjectName), 0);
            if (sameDayCount >= 2) {
                continue;
            }

            String teacherUsername = pickTeacherForSlot(slot, assignments, candidateTeachers, occupiedTeachers, fixedTeacherUsername);
            if (teacherUsername == null) {
                continue;
            }

            Long roomId = pickRoomForSlot(classId, subjectName, slot, occupiedRooms, assignments);
            if (roomId == null) {
                continue;
            }

            int score = computeAssignmentScore(subjectName, slot, assignments, sameDayCount, teacherUsername, roomId);
            if (best == null || score > bestScore) {
                best = new SlotAssignment(slot, subjectId, teacherUsername, roomId);
                bestScore = score;
            }
        }
        return best;
    }

    private int computeAssignmentScore(String subjectName, Slot slot, List<SlotAssignment> assignments, int sameDayCount,
                                       String teacherUsername, Long roomId) {
        int score = 100;
        if (isHeavySubject(subjectName)) {
            score += Math.max(0, 6 - slot.indexInDay()) * 5;
        } else {
            score += Math.max(0, 5 - slot.indexInDay()) * 2;
        }
        score -= sameDayCount * 20;
        score -= teacherLoad(assignments, teacherUsername) * 6;
        score -= existingTeacherLoad(teacherUsername) * 2;
        score -= assignments.stream().mapToInt(entry -> Objects.equals(entry.roomId(), roomId) ? 1 : 0).sum();
        score -= countAssignmentsForDay(assignments, slot.weekday()) * 3;
        return score;
    }

    private Long pickRoomForSlot(Long classId, String subjectName, Slot slot, Map<String, String> occupiedRooms, List<SlotAssignment> assignments) {
        for (Long roomId : candidateRoomIds(classId, subjectName)) {
            if (occupiedRooms.containsKey(slotKey(slot.weekday(), slot.indexInDay(), roomId))) {
                continue;
            }
            boolean usedByThisClass = assignments.stream().anyMatch(entry -> Objects.equals(entry.roomId(), roomId)
                    && entry.slot().weekday() == slot.weekday()
                    && entry.slot().indexInDay() == slot.indexInDay());
            if (!usedByThisClass) {
                return roomId;
            }
        }
        return null;
    }

    private String pickTeacherForSlot(Slot slot, List<SlotAssignment> assignments,
                                      List<String> candidateTeachers, Map<String, String> occupiedTeachers, String fixedTeacherUsername) {
        List<String> pool = fixedTeacherUsername == null ? candidateTeachers : List.of(fixedTeacherUsername);
        return pool.stream()
                .filter(username -> !occupiedTeachers.containsKey(slotKey(slot.weekday(), slot.indexInDay(), username)))
                .filter(username -> assignments.stream().noneMatch(entry -> username.equals(entry.teacherUsername())
                        && entry.slot().weekday() == slot.weekday()
                        && entry.slot().indexInDay() == slot.indexInDay()))
                .min(Comparator.comparingInt((String username) -> teacherLoad(assignments, username))
                        .thenComparingInt(this::existingTeacherLoad)
                        .thenComparing(String::compareTo))
                .orElse(null);
    }

    private List<Slot> buildSlotsForClass(int totalHours) {
        if (totalHours > WEEK_DAYS * SLOTS_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Planul depaseste numarul maxim de sloturi disponibile.");
        }

        List<Integer> dayLoads = new ArrayList<>();
        int remaining = totalHours;
        for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
            int daysLeft = WEEK_DAYS - weekday + 1;
            int minForToday = (int) Math.ceil(remaining / (double) daysLeft);
            int load = Math.min(SLOTS_PER_DAY, minForToday);
            dayLoads.add(load);
            remaining -= load;
        }

        List<Slot> slots = new ArrayList<>();
        for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
            int load = dayLoads.get(weekday - 1);
            for (int indexInDay = 1; indexInDay <= load; indexInDay++) {
                slots.add(new Slot(weekday, indexInDay));
            }
        }
        return slots;
    }

    private int countAssignmentsForDay(List<SlotAssignment> assignments, int weekday) {
        return (int) assignments.stream().filter(entry -> entry.slot().weekday() == weekday).count();
    }
    private int teacherLoad(List<SlotAssignment> assignments, String username) {
        return (int) assignments.stream().filter(entry -> username.equals(entry.teacherUsername())).count();
    }

    private int existingTeacherLoad(String username) {
        return (int) timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> username.equals(entry.teacherUsername()))
                .count();
    }

    private String assignedTeacherForClassSubject(Long classId, Long subjectId, Long ignoredEntryId) {
        return timetablesByClassId.getOrDefault(classId, List.of()).stream()
                .filter(entry -> !Objects.equals(entry.id(), ignoredEntryId))
                .filter(entry -> Objects.equals(entry.subjectId(), subjectId))
                .map(TimetableEntry::teacherUsername)
                .findFirst()
                .orElse(null);
    }
    private String validateTeacherAvailability(String teacherUsername, Long subjectId, Long ignoredEntryId, Integer weekday, Integer indexInDay) {
        if (teacherUsername == null || teacherUsername.isBlank()) {
            return selectTeacherForSubject(subjectId, ignoredEntryId, weekday, indexInDay);
        }
        if (!teachersBySubjectId.getOrDefault(subjectId, List.of()).contains(teacherUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profesorul selectat nu poate preda materia aleasa.");
        }
        boolean conflict = timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> !Objects.equals(entry.id(), ignoredEntryId))
                .anyMatch(entry -> teacherUsername.equals(entry.teacherUsername())
                        && Objects.equals(entry.weekday(), weekday)
                        && Objects.equals(entry.indexInDay(), indexInDay));
        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profesorul este deja ocupat in acest interval. Da refresh si alege alta varianta.");
        }
        return teacherUsername;
    }

    private String selectTeacherForSubject(Long subjectId, Long ignoredEntryId, Integer weekday, Integer indexInDay) {
        return teachersBySubjectId.getOrDefault(subjectId, List.of()).stream()
                .filter(username -> timetablesByClassId.values().stream()
                        .flatMap(Collection::stream)
                        .filter(entry -> !Objects.equals(entry.id(), ignoredEntryId))
                        .noneMatch(entry -> username.equals(entry.teacherUsername())
                                && Objects.equals(entry.weekday(), weekday)
                                && Objects.equals(entry.indexInDay(), indexInDay)))
                .min(Comparator.comparingInt(this::existingTeacherLoad).thenComparing(String::compareTo))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Nu exista profesor disponibil pentru materia selectata in acest interval."));
    }

    private void validateRoomAvailability(Long roomId, Long ignoredEntryId, Integer weekday, Integer indexInDay) {
        boolean conflict = timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> !Objects.equals(entry.id(), ignoredEntryId))
                .anyMatch(entry -> Objects.equals(entry.roomId(), roomId)
                        && Objects.equals(entry.weekday(), weekday)
                        && Objects.equals(entry.indexInDay(), indexInDay));
        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sala este deja ocupata in acest interval. Da refresh si alege alta sala.");
        }
    }

    private Room defaultRoomForSubject(Long classId, String subjectName, Integer weekday, Integer indexInDay, Long ignoredEntryId) {
        for (Long roomId : candidateRoomIds(classId, subjectName)) {
            boolean conflict = timetablesByClassId.values().stream()
                    .flatMap(Collection::stream)
                    .filter(entry -> !Objects.equals(entry.id(), ignoredEntryId))
                    .anyMatch(entry -> Objects.equals(entry.roomId(), roomId)
                            && Objects.equals(entry.weekday(), weekday)
                            && Objects.equals(entry.indexInDay(), indexInDay));
            if (!conflict) {
                return requireRoom(roomId);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Nu exista sala disponibila pentru materia selectata in acest interval.");
    }

    private Map<String, String> occupiedTeachers(Long ignoredClassId) {
        Map<String, String> occupied = new LinkedHashMap<>();
        for (Map.Entry<Long, List<TimetableEntry>> bucket : timetablesByClassId.entrySet()) {
            if (Objects.equals(bucket.getKey(), ignoredClassId)) {
                continue;
            }
            for (TimetableEntry entry : bucket.getValue()) {
                occupied.put(slotKey(entry.weekday(), entry.indexInDay(), entry.teacherUsername()), entry.className());
            }
        }
        return occupied;
    }

    private Map<String, String> occupiedRooms(Long ignoredClassId) {
        Map<String, String> occupied = new LinkedHashMap<>();
        for (Map.Entry<Long, List<TimetableEntry>> bucket : timetablesByClassId.entrySet()) {
            if (Objects.equals(bucket.getKey(), ignoredClassId)) {
                continue;
            }
            for (TimetableEntry entry : bucket.getValue()) {
                occupied.put(slotKey(entry.weekday(), entry.indexInDay(), entry.roomId()), entry.className());
            }
        }
        return occupied;
    }

    private LinkedHashMap<String, Integer> timetablePlanForClass(SchoolClass schoolClass) {
        LinkedHashMap<String, Integer> plan = new LinkedHashMap<>(curriculumPlanService.hoursForClass(schoolClass.name(), schoolClass.profile()));
        List<String> prioritySubjects = plan.keySet().stream()
                .sorted(Comparator.comparingInt((String subjectName) -> plan.getOrDefault(subjectName, 0)).reversed()
                        .thenComparing(subjectName -> isHeavySubject(subjectName) ? 0 : 1)
                        .thenComparing(String::compareTo))
                .toList();

        int totalHours = plan.values().stream().mapToInt(Integer::intValue).sum();
        int pointer = 0;
        while (totalHours < 25 && !prioritySubjects.isEmpty()) {
            String subjectName = prioritySubjects.get(pointer % prioritySubjects.size());
            plan.put(subjectName, plan.getOrDefault(subjectName, 0) + 1);
            totalHours++;
            pointer++;
        }
        return plan;
    }

    private boolean isHeavySubject(String subjectName) {
        return Set.of("Limba si literatura romana", "Matematica", "Informatica", "Informatica intensiv", "Fizica", "Limba engleza").contains(subjectName);
    }

    private int specialRoomPriority(String subjectName, Room room) {
        if (room.name().startsWith("Laborator Informatica") && Set.of("Informatica", "Informatica intensiv", "TIC").contains(subjectName)) {
            return 0;
        }
        if (room.name().startsWith("Laborator Fizica") && "Fizica".equals(subjectName)) {
            return 0;
        }
        if (room.name().startsWith("Laborator Chimie") && "Chimie".equals(subjectName)) {
            return 0;
        }
        if (room.name().startsWith("Sala Sport") && "Educatie fizica".equals(subjectName)) {
            return 0;
        }
        return 1;
    }

    private List<Long> candidateRoomIds(Long classId, String subjectName) {
        List<Room> normalRooms = rooms.values().stream()
                .filter(room -> room.name().matches("\\d{3}"))
                .sorted(Comparator.comparing(Room::name))
                .toList();
        List<Room> specialRooms = rooms.values().stream()
                .filter(room -> !room.name().matches("\\d{3}"))
                .sorted(Comparator.comparingInt((Room room) -> specialRoomPriority(subjectName, room)).thenComparing(Room::name))
                .toList();

        if (Set.of("Informatica", "Informatica intensiv", "TIC").contains(subjectName)) {
            return specialRooms.stream().filter(room -> room.name().startsWith("Laborator Informatica")).map(Room::id).toList();
        }
        if ("Fizica".equals(subjectName)) {
            return specialRooms.stream().filter(room -> room.name().startsWith("Laborator Fizica")).map(Room::id).toList();
        }
        if ("Chimie".equals(subjectName)) {
            return specialRooms.stream().filter(room -> room.name().startsWith("Laborator Chimie")).map(Room::id).toList();
        }
        if ("Educatie fizica".equals(subjectName)) {
            return specialRooms.stream().filter(room -> room.name().startsWith("Sala Sport")).map(Room::id).toList();
        }

        List<Long> ordered = new ArrayList<>();
        Long homeRoomId = homeRoomIdsByClassId.get(classId);
        if (homeRoomId != null) {
            ordered.add(homeRoomId);
        }
        for (Room room : normalRooms) {
            if (!Objects.equals(room.id(), homeRoomId)) {
                ordered.add(room.id());
            }
        }
        return ordered;
    }

    private String slotKey(Integer weekday, Integer indexInDay) {
        return weekday + ":" + indexInDay;
    }

    private String slotKey(Integer weekday, Integer indexInDay, Object resourceId) {
        return weekday + ":" + indexInDay + ":" + resourceId;
    }

    private String daySubjectKey(Integer weekday, String subjectName) {
        return weekday + ":" + subjectName.toLowerCase(Locale.ROOT);
    }

    private List<TimetableEntry> copyEntries(List<TimetableEntry> entries) {
        return entries.stream()
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
                .toList();
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
        classes.clear();
        homeRoomIdsByClassId.clear();
        for (int index = 0; index < CLASS_COUNT; index++) {
            long classId = index + 1L;
            classes.put(classId, new SchoolClass(classId, CLASS_NAMES[index], CLASS_PROFILES[index]));
        }
    }

    private void seedSubjects() {
        subjects.clear();
        subjectIdsByName.clear();
        teachersBySubjectId.clear();
        long subjectId = 1L;
        for (String subjectName : curriculumPlanService.allSubjects()) {
            subjects.put(subjectId, new Subject(subjectId, subjectName));
            subjectIdsByName.put(subjectName, subjectId);
            subjectId++;
        }
    }

    private void seedRooms() {
        rooms.clear();
        long roomId = 1L;
        for (int floor = 1; floor <= 4; floor++) {
            for (int number = 1; number <= 15; number++) {
                String roomName = floor + String.format(Locale.ROOT, "%02d", number);
                rooms.put(roomId, new Room(roomId, roomName, 30));
                roomId++;
            }
        }
        rooms.put(roomId, new Room(roomId, "Laborator Informatica 1", 28)); roomId++;
        rooms.put(roomId, new Room(roomId, "Laborator Informatica 2", 28)); roomId++;
        rooms.put(roomId, new Room(roomId, "Laborator Informatica 3", 28)); roomId++;
        rooms.put(roomId, new Room(roomId, "Laborator Fizica 1", 28)); roomId++;
        rooms.put(roomId, new Room(roomId, "Laborator Fizica 2", 28)); roomId++;
        rooms.put(roomId, new Room(roomId, "Laborator Chimie 1", 28)); roomId++;
        rooms.put(roomId, new Room(roomId, "Laborator Chimie 2", 28)); roomId++;
        rooms.put(roomId, new Room(roomId, "Sala Sport 1", 35)); roomId++;
        rooms.put(roomId, new Room(roomId, "Sala Sport 2", 35));

        for (int index = 0; index < CLASS_COUNT; index++) {
            homeRoomIdsByClassId.put(index + 1L, index + 1L);
        }
    }

    private void seedProfiles() {
        profilesByUsername.clear();
        profileIds.set(1);

        addStaffProfile("sysadmin01", "sysadmin", "Marius", "Stoica");
        addStaffProfile("admin01", "admin", "Roxana", "Marin");
        addStaffProfile("secretariat01", "secretariat", "Daniela", "Popa");
        addStaffProfile("scheduler01", "scheduler", "Silviu", "Dobre");

        for (TeacherSeed teacher : teacherSeeds()) {
            addTeacherProfile(teacher);
        }

        for (int index = 1; index <= CLASS_COUNT * STUDENTS_PER_CLASS; index++) {
            long classId = ((index - 1) / STUDENTS_PER_CLASS) + 1L;
            SchoolClass schoolClass = requireClass(classId);
            String username = String.format(Locale.ROOT, "student%03d", index);
            String firstName = FIRST_NAMES[(index - 1) % FIRST_NAMES.length];
            String lastName = LAST_NAMES[((index - 1) * 3) % LAST_NAMES.length];
            profilesByUsername.put(username, new UserProfile(
                    profileIds.getAndIncrement(),
                    username,
                    "student",
                    firstName,
                    lastName,
                    username + "@timetable.local",
                    classId,
                    schoolClass.name(),
                    List.of()
            ));
        }
    }

    private void addStaffProfile(String username, String role, String firstName, String lastName) {
        profilesByUsername.put(username, new UserProfile(
                profileIds.getAndIncrement(),
                username,
                role,
                firstName,
                lastName,
                username + "@timetable.local",
                null,
                null,
                List.of()
        ));
    }

    private void addTeacherProfile(TeacherSeed teacher) {
        profilesByUsername.put(teacher.username(), new UserProfile(
                profileIds.getAndIncrement(),
                teacher.username(),
                "professor",
                teacher.firstName(),
                teacher.lastName(),
                teacher.username() + "@timetable.local",
                null,
                null,
                List.of(teacher.subjectName())
        ));
        Long subjectId = subjectIdsByName.get(teacher.subjectName());
        if (subjectId != null) {
            teachersBySubjectId.computeIfAbsent(subjectId, ignored -> new ArrayList<>()).add(teacher.username());
        }
    }

    private List<TeacherSeed> teacherSeeds() {
        return List.of(
                new TeacherSeed("romana01", "Mihaela", "Ionescu", "Limba si literatura romana"),
                new TeacherSeed("romana02", "Corina", "Pavel", "Limba si literatura romana"),
                new TeacherSeed("romana03", "Adrian", "Mocanu", "Limba si literatura romana"),
                new TeacherSeed("mate01", "Cristian", "Serban", "Matematica"),
                new TeacherSeed("mate02", "Irina", "Voicu", "Matematica"),
                new TeacherSeed("mate03", "Raluca", "Toma", "Matematica"),
                new TeacherSeed("sport01", "Dorin", "Avram", "Educatie fizica"),
                new TeacherSeed("sport02", "Lucian", "Ilie", "Educatie fizica"),
                new TeacherSeed("chimie01", "Alina", "Marin", "Chimie"),
                new TeacherSeed("chimie02", "Sorin", "Dumitru", "Chimie"),
                new TeacherSeed("fizica01", "Mircea", "Petrescu", "Fizica"),
                new TeacherSeed("fizica02", "Anca", "Stan", "Fizica"),
                new TeacherSeed("biologie01", "Laura", "Nistor", "Biologie"),
                new TeacherSeed("biologie02", "Paula", "Tudor", "Biologie"),
                new TeacherSeed("engleza01", "Simona", "Manole", "Limba engleza"),
                new TeacherSeed("engleza02", "Monica", "Diaconescu", "Limba engleza"),
                new TeacherSeed("engleza03", "Radu", "Oprea", "Limba engleza"),
                new TeacherSeed("franceza01", "Lavinia", "Coman", "Limba franceza"),
                new TeacherSeed("franceza02", "Mirela", "Ene", "Limba franceza"),
                new TeacherSeed("latina01", "Carmen", "Preda", "Limba latina"),
                new TeacherSeed("istorie01", "Dan", "Neagu", "Istorie"),
                new TeacherSeed("istorie02", "Oana", "Munteanu", "Istorie"),
                new TeacherSeed("geografie01", "Claudiu", "Barbu", "Geografie"),
                new TeacherSeed("geografie02", "Florina", "Florea", "Geografie"),
                new TeacherSeed("socioumane01", "Andrada", "Lazar", "Socio-umane"),
                new TeacherSeed("socioumane02", "Mihnea", "Dragomir", "Socio-umane"),
                new TeacherSeed("religie01", "Gabriel", "Constantin", "Religie"),
                new TeacherSeed("artistica01", "Diana", "Rosu", "Educatie artistica"),
                new TeacherSeed("tic01", "Bogdan", "Georgescu", "TIC"),
                new TeacherSeed("tic02", "Camelia", "Apostol", "TIC"),
                new TeacherSeed("info01", "Marian", "Radu", "Informatica"),
                new TeacherSeed("info02", "Alexandra", "Stoica", "Informatica"),
                new TeacherSeed("info03", "Sergiu", "Nedelcu", "Informatica"),
                new TeacherSeed("infoint01", "Catalin", "Tudose", "Informatica intensiv"),
                new TeacherSeed("infoint02", "Cezara", "Moldovan", "Informatica intensiv"),
                new TeacherSeed("antreprenoriala01", "Iulia", "Sandu", "Educatie antreprenoriala"),
                new TeacherSeed("literatura01", "Sabina", "Matei", "Literatura universala"),
                new TeacherSeed("stiinte01", "Violeta", "Enache", "Stiinte")
        );
    }
}







