package ro.timetable.reference.service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.common.dto.ApiDtos.ClassSummaryResponse;
import ro.timetable.common.dto.ApiDtos.MeResponse;
import ro.timetable.common.dto.ApiDtos.ProfileResponse;
import ro.timetable.common.dto.ApiDtos.ProfileSettingsResponse;
import ro.timetable.common.dto.ApiDtos.TimetableGenerationResponse;
import ro.timetable.common.dto.ApiDtos.TimetableMoveOptionsResponse;
import ro.timetable.common.dto.ApiDtos.TimetableMoveResponse;
import ro.timetable.common.dto.ApiDtos.TimetableSlotMoveOptionResponse;
import ro.timetable.common.dto.ApiDtos;
import ro.timetable.common.util.PersistentStateService;
import ro.timetable.catalog.model.StudentAbsence;
import ro.timetable.catalog.model.StudentGrade;
import ro.timetable.reference.model.Room;
import ro.timetable.reference.model.SchoolClass;
import ro.timetable.reference.model.Subject;
import ro.timetable.reference.model.UserProfile;
import ro.timetable.timetable.model.TimetableEntry;

@Service
public class SchoolDataService {

    private record TeacherSeed(String username, String firstName, String lastName, String subjectName) {
    }

    private record StudentIdentityDocument(String series, String serialNumber) {
    }

    private record Slot(int weekday, int indexInDay) {
    }

    private record SlotAssignment(Slot slot, Long subjectId, String teacherUsername, Long roomId) {
    }

    private record AssignmentCandidate(List<SlotAssignment> assignments, int score) {
    }

    private record TimetableMoveCandidate(List<TimetableEntry> updatedEntries, List<String> warnings, String mode) {
    }

    private static final int CLASS_COUNT = 10;
    private static final int STUDENTS_PER_CLASS = 20;
    private static final int WEEK_DAYS = 5;
    private static final int SLOTS_PER_DAY = 7;
    private static final int TIMETABLE_GENERATION_ATTEMPTS = 60;
    private static final Set<String> FILOLOGIE_PROFILE_SUBJECTS = Set.of(
            "Limba si literatura romana",
            "Istorie",
            "Geografie"
    );
    private static final Set<String> STEM_PROFILE_SUBJECTS = Set.of(
            "Limba si literatura romana",
            "Matematica",
            "Fizica",
            "Informatica",
            "Informatica intensiv"
    );
    private static final Set<String> LEGACY_DEMO_TEACHER_USERNAMES = Set.of(
            "romana01", "romana02", "romana03",
            "mate01", "mate02", "mate03",
            "sport01", "sport02",
            "chimie01", "chimie02",
            "fizica01", "fizica02",
            "biologie01", "biologie02",
            "engleza01", "engleza02", "engleza03",
            "franceza01", "franceza02",
            "latina01",
            "istorie01", "istorie02",
            "geografie01", "geografie02",
            "socioumane01", "socioumane02",
            "religie01",
            "artistica01",
            "tic01", "tic02",
            "info01", "info02", "info03",
            "infoint01", "infoint02",
            "antreprenoriala01",
            "literatura01",
            "stiinte01"
    );
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
    private static final String STUDENT_CITY = "Campulung Muscel";
    private static final String STUDENT_COUNTY = "Arges";
    private static final int ARGES_COUNTY_CODE = 3;
    private static final long STUDENT_IDENTITY_RANDOM_SEED = 20260322L;
    private static final String CNP_CONTROL_KEY = "279146358279";
    private static final String ID_SERIES_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String[] STREET_NAMES = {
            "Negru Voda", "Republicii", "Matei Basarab", "Plevnei", "Cuza Voda",
            "General Dragalina", "Victoriei", "Eroilor", "Ion Luca Caragiale", "Alexandru cel Bun",
            "Colonel Stancu", "Nicolae Balcescu", "Mihai Eminescu", "Trandafirilor", "Primaverii"
    };
    private static final String[] BLOCK_NAMES = {
            "A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "D1", "E1"
    };

    private final CurriculumPlanService curriculumPlanService;
    private final PersistentStateService persistentStateService;
    private final ReferenceDataPersistenceService referenceDataPersistenceService;
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

    public SchoolDataService(
            CurriculumPlanService curriculumPlanService,
            PersistentStateService persistentStateService,
            ReferenceDataPersistenceService referenceDataPersistenceService
    ) {
        this.curriculumPlanService = curriculumPlanService;
        this.persistentStateService = persistentStateService;
        this.referenceDataPersistenceService = referenceDataPersistenceService;
    }

    @PostConstruct
    void init() {
        initializeReferenceData();
        loadPersistedTimetables();
        synchronizeDemoTeacherRoster();
        reconcileHomeroomAssignmentsWithTimetables();
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
        return findProfileByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
    }

    public boolean hasProfile(String username) {
        return findProfileByUsername(username).isPresent();
    }

    public UserProfile resolveAuthenticatedProfile(String username, String email) {
        return findProfileByUsername(username)
                .or(() -> findProfileByEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
    }

    public String resolveAuthenticatedUsername(String username, String email) {
        return resolveAuthenticatedProfile(username, email).username();
    }

    public ProfileResponse createManagedProfile(
            String username,
            String role,
            String firstName,
            String lastName,
            String email,
            Long classId,
            List<String> subjectsTaught
    ) {
        String normalizedUsername = normalizeUsername(username, "Username-ul este obligatoriu");
        String normalizedRole = normalizeRequiredProfileField(role, "Rolul este obligatoriu").toLowerCase(Locale.ROOT);
        String normalizedFirstName = normalizeRequiredProfileField(firstName, "Prenumele este obligatoriu");
        String normalizedLastName = normalizeRequiredProfileField(lastName, "Numele este obligatoriu");
        String normalizedEmail = normalizeRequiredProfileField(email, "Email-ul este obligatoriu");

        if (hasProfile(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username folosit deja");
        }

        if (referenceDataPersistenceService.emailUsedByAnotherProfile(normalizedEmail, normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email folosit deja");
        }

        if (!(normalizedRole.equals("student")
                || normalizedRole.equals("professor")
                || normalizedRole.equals("secretariat")
                || normalizedRole.equals("scheduler")
                || normalizedRole.equals("admin")
                || normalizedRole.equals("sysadmin"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol invalid");
        }

        SchoolClass schoolClass = null;
        String generatedAddress = null;
        String generatedCnp = null;
        String generatedIdSeries = null;
        String generatedSerialNumber = null;
        String generatedFatherInitial = null;
        List<String> normalizedSubjects = normalizeSubjectNames(subjectsTaught);

        if ("student".equals(normalizedRole)) {
            if (classId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clasa este obligatorie pentru elevi");
            }
            schoolClass = requireClass(classId);
            generatedAddress = generateUniqueStudentAddress(usedStudentAddresses(), new Random(System.nanoTime()));
            generatedCnp = generateUniqueStudentCnp(
                    usedStudentCnps(),
                    new Random(System.nanoTime() ^ normalizedUsername.hashCode()),
                    schoolClass.name()
            );
            StudentIdentityDocument generatedIdentityDocument = generateUniqueStudentIdentityDocument(
                    usedStudentIdentityDocumentKeys(),
                    new Random(System.nanoTime() ^ normalizedEmail.hashCode() ^ normalizedUsername.hashCode())
            );
            generatedIdSeries = generatedIdentityDocument.series();
            generatedSerialNumber = generatedIdentityDocument.serialNumber();
            generatedFatherInitial = generateStudentFatherInitial(new Random(System.nanoTime() ^ normalizedFirstName.hashCode() ^ normalizedLastName.hashCode()));
            normalizedSubjects = List.of();
        } else {
            if (classId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doar elevii pot avea clasa setata la creare");
            }
            if (!"professor".equals(normalizedRole)) {
                normalizedSubjects = List.of();
            } else if (normalizedSubjects.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profesorii trebuie sa aiba cel putin o materie");
            }
        }

        UserProfile profile = new UserProfile(
                profileIds.getAndIncrement(),
                1,
                normalizedUsername,
                normalizedRole,
                normalizedFirstName,
                normalizedLastName,
                normalizedEmail,
                generatedAddress,
                generatedCnp,
                generatedIdSeries,
                generatedSerialNumber,
                generatedFatherInitial,
                schoolClass == null ? null : schoolClass.id(),
                schoolClass == null ? null : schoolClass.name(),
                normalizedSubjects
        );
        profilesByUsername.put(normalizedUsername, profile);
        referenceDataPersistenceService.saveUserProfile(profile);
        rebuildDerivedIndexes();
        return profileResponse(profile);
    }

    public UserProfile updateProfile(
            String username,
            Integer version,
            String firstName,
            String lastName,
            String email,
            Long classId,
            String address,
            String cnp,
            String series,
            String serialNumber,
            String fatherInitial,
            Long homeroomClassId
    ) {
        UserProfile existing = getProfile(username);
        int existingVersion = existing.version() == null ? 1 : existing.version();
        if (!Objects.equals(existingVersion, version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profilul a fost modificat intre timp. Da refresh si incearca din nou.");
        }
        String normalizedFirstName = normalizeRequiredProfileField(firstName, "Prenumele este obligatoriu");
        String normalizedLastName = normalizeRequiredProfileField(lastName, "Numele este obligatoriu");
        String normalizedEmail = normalizeRequiredProfileField(email, "Email-ul este obligatoriu");
        String normalizedAddress = normalizeOptionalProfileField(address);
        String normalizedCnp = normalizeOptionalProfileField(cnp);
        String normalizedSeries = normalizeStudentIdentitySeries(series);
        String normalizedSerialNumber = normalizeStudentIdentitySerialNumber(serialNumber);
        String normalizedFatherInitial = normalizeStudentFatherInitial(fatherInitial);
        SchoolClass schoolClass = classId == null ? null : requireClass(classId);

        if (referenceDataPersistenceService.emailUsedByAnotherProfile(normalizedEmail, existing.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email folosit deja");
        }

        if ("student".equals(existing.role())) {
            if (schoolClass == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clasa este obligatorie pentru elevi");
            }
            if (normalizedAddress == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresa este obligatorie pentru elevi");
            }
            if (normalizedCnp == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP-ul este obligatoriu pentru elevi");
            }
            if (!hasValidStudentIdentityDocument(normalizedSeries, normalizedSerialNumber)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seria si numarul actului de identitate sunt invalide");
            }
            if (!hasValidStudentFatherInitial(normalizedFatherInitial)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Initiala tatalui este invalida");
            }
        }

        if (normalizedCnp != null) {
            validateCnp(normalizedCnp);
            if (referenceDataPersistenceService.cnpUsedByAnotherProfile(normalizedCnp, existing.username())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CNP folosit deja");
            }
        }

        if ("student".equals(existing.role())
                && referenceDataPersistenceService.studentIdentityDocumentUsedByAnotherProfile(
                normalizedSeries,
                normalizedSerialNumber,
                existing.username()
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seria si numarul actului de identitate sunt deja folosite");
        }

        if (!"professor".equals(existing.role()) && homeroomClassId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doar profesorii pot fi diriginti");
        }

        UserProfile updated = new UserProfile(
                existing.id(),
                existingVersion + 1,
                existing.username(),
                existing.role(),
                normalizedFirstName,
                normalizedLastName,
                normalizedEmail,
                normalizedAddress,
                normalizedCnp,
                "student".equals(existing.role()) ? normalizedSeries : existing.idSeries(),
                "student".equals(existing.role()) ? normalizedSerialNumber : existing.serialNumber(),
                "student".equals(existing.role()) ? normalizedFatherInitial : existing.fatherInitial(),
                schoolClass == null ? null : schoolClass.id(),
                schoolClass == null ? null : schoolClass.name(),
                existing.subjectsTaught()
        );

        profilesByUsername.put(existing.username(), updated);
        updateHomeroomTeacherAssignment(existing, updated, homeroomClassId);
        synchronizeTeacherDisplayName(existing, updated);
        referenceDataPersistenceService.saveUserProfile(updated);
        return updated;
    }

    public MeResponse updateMyProfile(
            String username,
            List<String> roles,
            Map<String, Object> claims,
            Integer version,
            String firstName,
            String lastName,
            String email,
            String address,
            boolean emailNotificationsEnabled,
            boolean inAppNotificationsEnabled
    ) {
        UserProfile existing = getProfile(username);
        SchoolClass homeroomClass = "professor".equals(existing.role()) ? homeroomClassForTeacher(existing.username()) : null;

        updateProfile(
                username,
                version,
                firstName,
                lastName,
                email,
                existing.classId(),
                address,
                existing.cnp(),
                existing.idSeries(),
                existing.serialNumber(),
                existing.fatherInitial(),
                homeroomClass == null ? null : homeroomClass.id()
        );
        referenceDataPersistenceService.updateProfileSettings(username, emailNotificationsEnabled, inAppNotificationsEnabled);
        return meResponse(username, roles, claims);
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

    public List<UserProfile> getUserProfilesByRole(String role) {
        return profilesByUsername.values().stream()
                .filter(profile -> role == null || role.isBlank() || role.equalsIgnoreCase(profile.role()))
                .sorted(Comparator.comparing(UserProfile::className, Comparator.nullsLast(String::compareTo))
                        .thenComparing(UserProfile::lastName)
                        .thenComparing(UserProfile::firstName)
                        .thenComparing(UserProfile::username))
                .toList();
    }

    public List<ProfileResponse> getProfilesByRole(String role) {
        return getProfilesByRole(role, false);
    }

    public List<ProfileResponse> getProfilesByRole(String role, boolean includeSensitive) {
        return getUserProfilesByRole(role).stream()
                .map(profile -> toProfileResponse(profile, includeSensitive))
                .toList();
    }

    public List<TimetableEntry> getTimetableForClass(Long classId) {
        requireClass(classId);
        return copyEntries(timetablesByClassId.getOrDefault(classId, List.of()));
    }

    public List<TimetableEntry> getTimetableForTeacher(String username) {
        String canonicalUsername = getProfile(username).username();
        return timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> canonicalUsername.equals(entry.teacherUsername()))
                .sorted(Comparator.comparing(TimetableEntry::weekday).thenComparing(TimetableEntry::indexInDay))
                .toList();
    }

    public boolean canAccessTimetableForClass(String username, List<String> roles, Long classId) {
        requireClass(classId);

        if (hasAnyRole(roles, "secretariat", "scheduler", "admin", "sysadmin")) {
            return true;
        }

        if (roles.contains("student")) {
            UserProfile profile = getProfile(username);
            return Objects.equals(profile.classId(), classId);
        }

        return roles.contains("professor") && teachesClass(username, classId);
    }

    public boolean canManageTimetables(List<String> roles) {
        return hasAnyRole(roles, "secretariat", "scheduler", "admin", "sysadmin");
    }

    public TimetableGenerationResponse generateTimetable(Long classId) {
        SchoolClass schoolClass = requireClass(classId);
        List<TimetableEntry> generated = buildGeneratedTimetable(schoolClass, classId);
        timetablesByClassId.put(classId, generated);
        persistentStateService.replaceTimetableForClass(classId, generated);
        reconcileHomeroomAssignmentForClass(classId);
        return new TimetableGenerationResponse("Timetable generated", List.of(jobIds.incrementAndGet()));
    }

    public void deleteTimetable(Long classId) {
        requireClass(classId);
        timetablesByClassId.remove(classId);
        persistentStateService.deleteTimetable(classId);
        reconcileHomeroomAssignmentForClass(classId);
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
                  return updated;
              }
          }
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timetable entry not found");
      }

    public TimetableMoveOptionsResponse moveOptions(Long entryId, Integer entryVersion) {
        TimetableEntry source = requireTimetableEntry(entryId);
        ensureTimetableEntryVersion(source, entryVersion);

        List<TimetableSlotMoveOptionResponse> slotOptions = new ArrayList<>();
        for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
            for (int indexInDay = 1; indexInDay <= SLOTS_PER_DAY; indexInDay++) {
                if (Objects.equals(source.weekday(), weekday) && Objects.equals(source.indexInDay(), indexInDay)) {
                    slotOptions.add(new TimetableSlotMoveOptionResponse(
                            weekday,
                            indexInDay,
                            "same",
                            "stay",
                            source.id(),
                            source.subjectName(),
                            source.teacherName(),
                            source.roomName(),
                            List.of(),
                            null
                    ));
                    continue;
                }

                TimetableEntry targetEntry = findClassTimetableEntry(source.classId(), weekday, indexInDay, source.id());
                try {
                    TimetableMoveCandidate candidate = buildMoveCandidate(source, targetEntry, weekday, indexInDay, "swap-auto");
                    slotOptions.add(new TimetableSlotMoveOptionResponse(
                            weekday,
                            indexInDay,
                            candidate.warnings().isEmpty() ? "valid" : "warning",
                            candidate.mode(),
                            targetEntry == null ? null : targetEntry.id(),
                            targetEntry == null ? null : targetEntry.subjectName(),
                            targetEntry == null ? null : targetEntry.teacherName(),
                            targetEntry == null ? null : targetEntry.roomName(),
                            candidate.warnings(),
                            null
                    ));
                } catch (ResponseStatusException ex) {
                    slotOptions.add(new TimetableSlotMoveOptionResponse(
                            weekday,
                            indexInDay,
                            "blocked",
                            targetEntry == null ? "move" : "swap-auto",
                            targetEntry == null ? null : targetEntry.id(),
                            targetEntry == null ? null : targetEntry.subjectName(),
                            targetEntry == null ? null : targetEntry.teacherName(),
                            targetEntry == null ? null : targetEntry.roomName(),
                            List.of(),
                            ex.getReason()
                    ));
                }
            }
        }

        return new TimetableMoveOptionsResponse(
                source.id(),
                source.version(),
                source.weekday(),
                source.indexInDay(),
                slotOptions
        );
    }

    public TimetableMoveResponse moveEntry(
            Long entryId,
            Integer entryVersion,
            Integer targetWeekday,
            Integer targetIndexInDay,
            String mode
    ) {
        TimetableEntry source = requireTimetableEntry(entryId);
        ensureTimetableEntryVersion(source, entryVersion);
        if (targetWeekday == null || targetWeekday < 1 || targetWeekday > WEEK_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ziua selectata este invalida");
        }
        if (targetIndexInDay == null || targetIndexInDay < 1 || targetIndexInDay > SLOTS_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalul selectat este invalid");
        }

        TimetableEntry targetEntry = findClassTimetableEntry(source.classId(), targetWeekday, targetIndexInDay, source.id());
        TimetableMoveCandidate candidate = buildMoveCandidate(source, targetEntry, targetWeekday, targetIndexInDay, mode);
        applyTimetableMove(source.classId(), source.id(), targetEntry == null ? null : targetEntry.id(), candidate.updatedEntries());

        return new TimetableMoveResponse(
                "Orarul a fost actualizat manual.",
                candidate.mode(),
                candidate.warnings(),
                candidate.updatedEntries()
        );
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
    private void initializeReferenceData() {
        if (referenceDataPersistenceService.hasReferenceData()) {
            loadReferenceDataFromDatabase();
            return;
        }
        seedClasses();
        seedSubjects();
        seedRooms();
        seedProfiles();
        referenceDataPersistenceService.saveReferenceData(
                getClasses(),
                getSubjects(),
                getRooms(),
                new ArrayList<>(profilesByUsername.values())
        );
    }

    private void loadReferenceDataFromDatabase() {
        classes.clear();
        subjects.clear();
        rooms.clear();
        profilesByUsername.clear();

        referenceDataPersistenceService.loadClasses().forEach(schoolClass -> classes.put(schoolClass.id(), schoolClass));
        referenceDataPersistenceService.loadSubjects().forEach(subject -> subjects.put(subject.id(), subject));
        referenceDataPersistenceService.loadRooms().forEach(room -> rooms.put(room.id(), room));
        referenceDataPersistenceService.loadProfiles().forEach(profile -> profilesByUsername.put(profile.username(), profile));

        rebuildDerivedIndexes();
        backfillMissingStudentIdentityData();
    }

    private void rebuildDerivedIndexes() {
        subjectIdsByName.clear();
        teachersBySubjectId.clear();
        homeRoomIdsByClassId.clear();

        subjects.values().stream()
                .sorted(Comparator.comparing(Subject::id))
                .forEach(subject -> subjectIdsByName.put(subject.name(), subject.id()));

        profilesByUsername.values().stream()
                .filter(profile -> "professor".equals(profile.role()))
                .sorted(Comparator.comparing(UserProfile::username))
                .forEach(profile -> profile.subjectsTaught().forEach(subjectName -> {
                    Long subjectId = subjectIdsByName.get(subjectName);
                    if (subjectId != null) {
                        teachersBySubjectId.computeIfAbsent(subjectId, ignored -> new ArrayList<>()).add(profile.username());
                    }
                }));

        List<Long> normalRoomIds = rooms.values().stream()
                .filter(room -> room.name().matches("\\d{3}"))
                .sorted(Comparator.comparing(Room::name))
                .map(Room::id)
                .toList();

        List<SchoolClass> orderedClasses = classes.values().stream()
                .sorted(Comparator.comparing(SchoolClass::id))
                .toList();

        for (int index = 0; index < orderedClasses.size() && index < normalRoomIds.size(); index++) {
            homeRoomIdsByClassId.put(orderedClasses.get(index).id(), normalRoomIds.get(index));
        }

        long nextProfileId = profilesByUsername.values().stream()
                .map(UserProfile::id)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1;
        profileIds.set(nextProfileId);
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

    private void synchronizeDemoTeacherRoster() {
        Map<String, TeacherSeed> desiredTeachersByUsername = new LinkedHashMap<>();
        teacherSeeds().forEach(seed -> desiredTeachersByUsername.put(seed.username(), seed));

        List<UserProfile> legacyTeacherProfiles = profilesByUsername.values().stream()
                .filter(profile -> "professor".equals(profile.role()))
                .filter(profile -> LEGACY_DEMO_TEACHER_USERNAMES.contains(profile.username()))
                .sorted(Comparator.comparing(UserProfile::username))
                .toList();

        boolean changed = false;
        for (TeacherSeed teacher : desiredTeachersByUsername.values()) {
            UserProfile existing = profilesByUsername.get(teacher.username());
            UserProfile desired = teacherProfileFromSeed(teacher, existing);
            if (!Objects.equals(existing, desired)) {
                profilesByUsername.put(desired.username(), desired);
                referenceDataPersistenceService.saveUserProfile(desired);
                changed = true;
            }
        }

        List<UserProfile> removedTeachers = legacyTeacherProfiles.stream()
                .filter(profile -> !desiredTeachersByUsername.containsKey(profile.username()))
                .toList();

        if (removedTeachers.isEmpty() && !changed) {
            rebuildDerivedIndexes();
            return;
        }

        Map<String, String> replacementsByUsername = teacherReplacementMap(removedTeachers, desiredTeachersByUsername);
        removedTeachers.forEach(profile -> profilesByUsername.remove(profile.username()));
        rebuildDerivedIndexes();

        if (!removedTeachers.isEmpty()) {
            regeneratePersistedTimetablesForTeacherRoster();
            remapPersistedGradesForTeacherRoster(replacementsByUsername);
            remapPersistedAbsencesForTeacherRoster(replacementsByUsername);
            referenceDataPersistenceService.deleteUserProfilesByUsername(removedTeachers.stream().map(UserProfile::username).toList());
        }
    }

    private UserProfile teacherProfileFromSeed(TeacherSeed teacher, UserProfile existing) {
        return new UserProfile(
                existing == null ? profileIds.getAndIncrement() : existing.id(),
                existing == null ? 1 : existing.version(),
                teacher.username(),
                "professor",
                teacher.firstName(),
                teacher.lastName(),
                teacher.username() + "@timetable.local",
                existing == null ? null : existing.address(),
                existing == null ? null : existing.cnp(),
                existing == null ? null : existing.idSeries(),
                existing == null ? null : existing.serialNumber(),
                existing == null ? null : existing.fatherInitial(),
                null,
                null,
                List.of(teacher.subjectName())
        );
    }

    private Map<String, String> teacherReplacementMap(List<UserProfile> removedTeachers, Map<String, TeacherSeed> desiredTeachersByUsername) {
        Map<String, String> replacements = new LinkedHashMap<>();
        Map<String, String> replacementBySubject = new LinkedHashMap<>();
        desiredTeachersByUsername.values().forEach(seed -> replacementBySubject.putIfAbsent(seed.subjectName(), seed.username()));

        for (UserProfile removed : removedTeachers) {
            String subjectName = removed.subjectsTaught().isEmpty() ? null : removed.subjectsTaught().get(0);
            String replacementUsername = subjectName == null ? null : replacementBySubject.get(subjectName);
            if (replacementUsername != null) {
                replacements.put(removed.username(), replacementUsername);
            }
        }
        return replacements;
    }

    private void regeneratePersistedTimetablesForTeacherRoster() {
        List<Long> classIdsWithTimetables = timetablesByClassId.keySet().stream()
                .sorted()
                .toList();
        if (classIdsWithTimetables.isEmpty()) {
            return;
        }

        Map<Long, List<TimetableEntry>> backup = new LinkedHashMap<>();
        timetablesByClassId.forEach((classId, entries) -> backup.put(classId, copyEntries(entries)));

        Map<Long, List<TimetableEntry>> regenerated = new LinkedHashMap<>();
        timetablesByClassId.clear();
        try {
            for (Long classId : classIdsWithTimetables) {
                SchoolClass schoolClass = requireClass(classId);
                List<TimetableEntry> generated = buildGeneratedTimetable(schoolClass, classId);
                regenerated.put(classId, generated);
                timetablesByClassId.put(classId, generated);
            }
        } catch (RuntimeException exception) {
            timetablesByClassId.clear();
            backup.forEach((classId, entries) -> timetablesByClassId.put(classId, copyEntries(entries)));
            throw exception;
        }

        for (Long classId : classIdsWithTimetables) {
            persistentStateService.replaceTimetableForClass(classId, regenerated.getOrDefault(classId, List.of()));
        }
    }

    private void remapPersistedGradesForTeacherRoster(Map<String, String> replacementsByUsername) {
        List<StudentGrade> grades = persistentStateService.loadGrades();
        List<StudentGrade> updatedGrades = new ArrayList<>();
        for (StudentGrade grade : grades) {
            String replacementUsername = replacementsByUsername.get(grade.teacherUsername());
            if (replacementUsername == null) {
                continue;
            }
            UserProfile teacher = getProfile(replacementUsername);
            updatedGrades.add(new StudentGrade(
                    grade.id(),
                    grade.studentUsername(),
                    grade.studentName(),
                    grade.classId(),
                    grade.className(),
                    grade.subjectId(),
                    grade.subjectName(),
                    grade.gradeValue(),
                    grade.gradeDate(),
                    teacher.username(),
                    displayName(teacher),
                    grade.version()
            ));
        }
        persistentStateService.saveGrades(updatedGrades);
    }

    private void remapPersistedAbsencesForTeacherRoster(Map<String, String> replacementsByUsername) {
        List<StudentAbsence> absences = persistentStateService.loadAbsences();
        List<StudentAbsence> updatedAbsences = new ArrayList<>();
        for (StudentAbsence absence : absences) {
            String replacementUsername = replacementsByUsername.get(absence.teacherUsername());
            if (replacementUsername == null) {
                continue;
            }
            UserProfile teacher = getProfile(replacementUsername);
            updatedAbsences.add(new StudentAbsence(
                    absence.id(),
                    absence.studentUsername(),
                    absence.studentName(),
                    absence.classId(),
                    absence.className(),
                    absence.subjectId(),
                    absence.subjectName(),
                    absence.absenceDate(),
                    teacher.username(),
                    displayName(teacher),
                    absence.motivated(),
                    absence.motivatedByUsername(),
                    absence.motivatedByName(),
                    absence.motivatedAt(),
                    absence.version()
            ));
        }
        persistentStateService.saveAbsences(updatedAbsences);
    }

    public MeResponse meResponse(String username, List<String> roles, Map<String, Object> claims) {
        Object emailClaim = claims == null ? null : claims.get("email");
        UserProfile profile = resolveAuthenticatedProfile(username, emailClaim == null ? null : String.valueOf(emailClaim));
        SchoolClass schoolClass = profile.classId() == null ? null : requireClass(profile.classId());
        ProfileSettingsResponse settings = referenceDataPersistenceService.loadProfileSettings(profile.username());
        return new MeResponse(
                profile.id(),
                profile.version(),
                profile.username(),
                profile.firstName(),
                profile.lastName(),
                profile.email(),
                profile.address(),
                profile.cnp(),
                profile.idSeries(),
                profile.serialNumber(),
                profile.fatherInitial(),
                profile.role(),
                roles,
                profile.classId(),
                profile.className(),
                schoolClass == null ? null : schoolClass.profile(),
                profile.subjectsTaught(),
                claims,
                settings,
                schoolClass == null ? null : new ClassSummaryResponse(profile.classId(), profile.className(), schoolClass.profile())
        );
    }

    private ProfileResponse profileResponse(UserProfile profile) {
        return toProfileResponse(profile, true);
    }

    public ProfileResponse toProfileResponse(UserProfile profile, boolean includeSensitive) {
        SchoolClass schoolClass = profile.classId() == null ? null : requireClass(profile.classId());
        SchoolClass homeroomClass = "professor".equals(profile.role()) ? homeroomClassForTeacher(profile.username()) : null;
        return new ProfileResponse(
                profile.id(),
                profile.version(),
                profile.username(),
                profile.role(),
                profile.firstName(),
                profile.lastName(),
                profile.email(),
                includeSensitive ? profile.address() : null,
                includeSensitive ? profile.cnp() : null,
                includeSensitive ? profile.idSeries() : null,
                includeSensitive ? profile.serialNumber() : null,
                includeSensitive ? profile.fatherInitial() : null,
                profile.classId(),
                profile.className(),
                schoolClass == null ? null : schoolClass.profile(),
                profile.subjectsTaught(),
                homeroomClass == null ? null : homeroomClass.id(),
                homeroomClass == null ? null : homeroomClass.name()
        );
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
        AssignmentCandidate bestCandidate = null;
        ResponseStatusException lastFailure = null;
        for (int attempt = 0; attempt < TIMETABLE_GENERATION_ATTEMPTS; attempt++) {
            List<String> occurrences = new ArrayList<>(baseOccurrences);
            List<Slot> slots = new ArrayList<>(baseSlots);
            if (attempt > 0) {
                Collections.shuffle(occurrences, new Random(classId * 97 + attempt));
                Collections.shuffle(slots, new Random(classId * 211 + attempt));
            }
            try {
                List<SlotAssignment> candidateAssignments = tryBuildAssignments(schoolClass, classId, occurrences, slots, plan);
                // Keep the best valid timetable instead of stopping at the first schedulable one.
                int candidateScore = evaluateScheduleQuality(classId, plan, candidateAssignments);
                if (bestCandidate == null || candidateScore > bestCandidate.score()) {
                    bestCandidate = new AssignmentCandidate(candidateAssignments, candidateScore);
                }
            } catch (ResponseStatusException exception) {
                lastFailure = exception;
            }
        }

        if (bestCandidate != null) {
            return bestCandidate.assignments();
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Nu am putut genera un orar valid pentru " + schoolClass.name() + ".");
    }

    private List<SlotAssignment> tryBuildAssignments(
            SchoolClass schoolClass,
            Long classId,
            List<String> occurrences,
            List<Slot> slots,
            Map<String, Integer> subjectTargets
    ) {
        Map<String, String> occupiedTeachers = occupiedTeachers(classId);
        Map<String, String> occupiedRooms = occupiedRooms(classId);
        Map<String, Integer> daySubjectCounts = new LinkedHashMap<>();
        Map<String, Integer> assignedSubjectCounts = new LinkedHashMap<>();
        Map<Long, String> teacherBySubjectId = new LinkedHashMap<>();
        List<SlotAssignment> assignments = new ArrayList<>();
        Set<String> usedSlots = new HashSet<>();

        for (String subjectName : occurrences) {
            Long subjectId = subjectIdsByName.get(subjectName);
            if (subjectId == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing subject " + subjectName);
            }
            String fixedTeacherUsername = teacherBySubjectId.get(subjectId);
            String preferredTeacherUsername = fixedTeacherUsername != null ? fixedTeacherUsername : preferredTeacherForClassSubject(classId, subjectId);
            SlotAssignment best = pickBestAssignment(
                    classId,
                    subjectId,
                    subjectName,
                    fixedTeacherUsername,
                    preferredTeacherUsername,
                    slots,
                    assignments,
                    usedSlots,
                    daySubjectCounts,
                    assignedSubjectCounts,
                    subjectTargets,
                    occupiedTeachers,
                    occupiedRooms
            );
            if (best == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Nu am putut genera un orar valid pentru " + schoolClass.name() + ".");
            }
            teacherBySubjectId.putIfAbsent(subjectId, best.teacherUsername());
            assignments.add(best);
            usedSlots.add(slotKey(best.slot().weekday(), best.slot().indexInDay()));
            occupiedTeachers.put(slotKey(best.slot().weekday(), best.slot().indexInDay(), best.teacherUsername()), schoolClass.name());
            occupiedRooms.put(slotKey(best.slot().weekday(), best.slot().indexInDay(), best.roomId()), schoolClass.name());
            daySubjectCounts.merge(daySubjectKey(best.slot().weekday(), subjectName), 1, Integer::sum);
            assignedSubjectCounts.merge(subjectName, 1, Integer::sum);
        }

        return assignments;
    }

    private SlotAssignment pickBestAssignment(
            Long classId,
            Long subjectId,
            String subjectName,
            String fixedTeacherUsername,
            String preferredTeacherUsername,
            List<Slot> slots,
            List<SlotAssignment> assignments,
            Set<String> usedSlots,
            Map<String, Integer> daySubjectCounts,
            Map<String, Integer> assignedSubjectCounts,
            Map<String, Integer> subjectTargets,
            Map<String, String> occupiedTeachers,
            Map<String, String> occupiedRooms
    ) {
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
            if (prefersMiddayForProfile(classId, subjectName) && slot.indexInDay() == 1) {
                continue;
            }

            int sameDayCount = daySubjectCounts.getOrDefault(daySubjectKey(slot.weekday(), subjectName), 0);
            if (sameDayCount >= 2) {
                continue;
            }

            String teacherUsername = pickTeacherForSlot(slot, assignments, candidateTeachers, occupiedTeachers, fixedTeacherUsername, preferredTeacherUsername);
            if (teacherUsername == null) {
                continue;
            }

            Long roomId = pickRoomForSlot(classId, subjectName, slot, occupiedRooms, assignments);
            if (roomId == null) {
                continue;
            }

            int score = computeAssignmentScore(
                    classId,
                    subjectName,
                    slot,
                    assignments,
                    sameDayCount,
                    assignedSubjectCounts,
                    subjectTargets,
                    teacherUsername,
                    roomId
            );
            if (best == null || score > bestScore) {
                best = new SlotAssignment(slot, subjectId, teacherUsername, roomId);
                bestScore = score;
            }
        }
        return best;
    }

    private int computeAssignmentScore(
            Long classId,
            String subjectName,
            Slot slot,
            List<SlotAssignment> assignments,
            int sameDayCount,
            Map<String, Integer> assignedSubjectCounts,
            Map<String, Integer> subjectTargets,
            String teacherUsername,
            Long roomId
    ) {
        int score = 100;
        int totalOccurrences = subjectTargets.getOrDefault(subjectName, 1);
        int assignedOccurrences = assignedSubjectCounts.getOrDefault(subjectName, 0);
        int remainingOccurrencesAfterThis = Math.max(0, totalOccurrences - assignedOccurrences - 1);
        long distinctDaysUsed = countDistinctDaysForSubject(assignments, subjectName);
        long distinctDaysAfterThis = distinctDaysUsed + (sameDayCount == 0 ? 1 : 0);
        int remainingDistinctDays = Math.max(0, WEEK_DAYS - (int) distinctDaysAfterThis);
        if (prefersMiddayForProfile(classId, subjectName)) {
            if (slot.indexInDay() == 2) {
                score -= 18;
            } else if (slot.indexInDay() <= 4) {
                score += 18;
            } else if (slot.indexInDay() == 5) {
                score += 8;
            } else {
                score -= (slot.indexInDay() - 5) * 5;
            }
        } else if (isHeavySubject(subjectName)) {
            if (slot.indexInDay() <= 2) {
                score += 18;
            } else if (slot.indexInDay() <= 4) {
                score += 10;
            } else {
                score -= (slot.indexInDay() - 4) * 12;
            }
        } else {
            score += Math.max(0, 5 - slot.indexInDay()) * 2;
            if (slot.indexInDay() >= 6) {
                score -= (slot.indexInDay() - 5) * 4;
            }
        }
        if (sameDayCount > 0) {
            score -= totalOccurrences <= WEEK_DAYS ? 42 : 24;
            if (remainingOccurrencesAfterThis <= remainingDistinctDays) {
                score -= 18;
            }
        }
        if (hasAdjacentAssignmentWithSubject(assignments, slot, subjectName)) {
            score -= 28;
        }
        score -= teacherLoad(assignments, teacherUsername) * 6;
        score -= teacherLoadForDay(assignments, teacherUsername, slot.weekday()) * 5;
        score -= existingTeacherLoad(teacherUsername) * 2;
        score -= existingTeacherLoadForDay(teacherUsername, slot.weekday()) * 3;
        score -= assignments.stream().mapToInt(entry -> Objects.equals(entry.roomId(), roomId) ? 1 : 0).sum();
        score -= countAssignmentsForDay(assignments, slot.weekday()) * 3;
        if (usesPreferredHomeRoom(classId, subjectName, roomId)) {
            score += 8;
        }
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
                                      List<String> candidateTeachers, Map<String, String> occupiedTeachers,
                                      String fixedTeacherUsername, String preferredTeacherUsername) {
        List<String> pool = new ArrayList<>();
        if (fixedTeacherUsername != null) {
            pool.add(fixedTeacherUsername);
        } else {
            if (preferredTeacherUsername != null && candidateTeachers.contains(preferredTeacherUsername)) {
                pool.add(preferredTeacherUsername);
            }
            candidateTeachers.stream()
                    .filter(username -> !pool.contains(username))
                    .forEach(pool::add);
        }

        return pool.stream()
                .filter(username -> !occupiedTeachers.containsKey(slotKey(slot.weekday(), slot.indexInDay(), username)))
                .filter(username -> assignments.stream().noneMatch(entry -> username.equals(entry.teacherUsername())
                        && entry.slot().weekday() == slot.weekday()
                        && entry.slot().indexInDay() == slot.indexInDay()))
                .min(Comparator.comparingInt((String username) -> preferredTeacherUsername != null && preferredTeacherUsername.equals(username) ? 0 : 1)
                        .thenComparingInt(username -> teacherLoad(assignments, username))
                        .thenComparingInt(username -> teacherLoadForDay(assignments, username, slot.weekday()))
                        .thenComparingInt(username -> existingTeacherLoadForDay(username, slot.weekday()))
                        .thenComparingInt(this::existingTeacherLoad)
                        .thenComparing(String::compareTo))
                .orElse(null);
    }

    private String preferredTeacherForClassSubject(Long classId, Long subjectId) {
        List<String> teachers = teachersBySubjectId.getOrDefault(subjectId, List.of());
        if (teachers.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(Math.toIntExact(classId - 1), teachers.size());
        return teachers.get(index);
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

    private int countAssignmentsForSubjectOnDay(List<SlotAssignment> assignments, int weekday, String subjectName) {
        return (int) assignments.stream()
                .filter(entry -> entry.slot().weekday() == weekday)
                .filter(entry -> subjectName.equals(subjectName(entry)))
                .count();
    }

    private long countDistinctDaysForSubject(List<SlotAssignment> assignments, String subjectName) {
        return assignments.stream()
                .filter(entry -> subjectName.equals(subjectName(entry)))
                .map(entry -> entry.slot().weekday())
                .distinct()
                .count();
    }

    private boolean hasAdjacentAssignmentWithSubject(List<SlotAssignment> assignments, Slot slot, String subjectName) {
        return assignments.stream()
                .filter(entry -> entry.slot().weekday() == slot.weekday())
                .filter(entry -> Math.abs(entry.slot().indexInDay() - slot.indexInDay()) == 1)
                .anyMatch(entry -> subjectName.equals(subjectName(entry)));
    }

    private int teacherLoad(List<SlotAssignment> assignments, String username) {
        return (int) assignments.stream().filter(entry -> username.equals(entry.teacherUsername())).count();
    }

    private int teacherLoadForDay(List<SlotAssignment> assignments, String username, int weekday) {
        return (int) assignments.stream()
                .filter(entry -> username.equals(entry.teacherUsername()))
                .filter(entry -> entry.slot().weekday() == weekday)
                .count();
    }

    private int existingTeacherLoad(String username) {
        return (int) timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> username.equals(entry.teacherUsername()))
                .count();
    }

    private int existingTeacherLoadForDay(String username, int weekday) {
        return (int) timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> username.equals(entry.teacherUsername()))
                .filter(entry -> Objects.equals(entry.weekday(), weekday))
                .count();
    }

    private int evaluateScheduleQuality(Long classId, Map<String, Integer> subjectTargets, List<SlotAssignment> assignments) {
        // Favor timetables that spread repeated subjects across the week and keep demanding classes earlier.
        int score = 0;
        for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
            int day = weekday;
            List<SlotAssignment> dayAssignments = assignments.stream()
                    .filter(entry -> entry.slot().weekday() == day)
                    .sorted(Comparator.comparingInt(entry -> entry.slot().indexInDay()))
                    .toList();

            for (int index = 0; index < dayAssignments.size(); index++) {
                SlotAssignment current = dayAssignments.get(index);
                String subjectName = subjectName(current);
                if (prefersMiddayForProfile(classId, subjectName) && current.slot().indexInDay() == 2) {
                    score -= 12;
                } else if (isHeavySubject(subjectName) && current.slot().indexInDay() >= 5) {
                    score -= (current.slot().indexInDay() - 4) * 8;
                }
                if (usesPreferredHomeRoom(classId, subjectName, current.roomId())) {
                    score += 2;
                }
                if (index > 0 && Objects.equals(dayAssignments.get(index - 1).subjectId(), current.subjectId())) {
                    score -= 40;
                }
            }
        }

        for (Map.Entry<String, Integer> subjectTarget : subjectTargets.entrySet()) {
            String subjectName = subjectTarget.getKey();
            int totalOccurrences = subjectTarget.getValue();
            long distinctDays = countDistinctDaysForSubject(assignments, subjectName);
            if (totalOccurrences <= WEEK_DAYS) {
                score += (int) distinctDays * 10;
            }
            for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
                int occurrencesPerDay = countAssignmentsForSubjectOnDay(assignments, weekday, subjectName);
                if (occurrencesPerDay > 1 && totalOccurrences <= WEEK_DAYS) {
                    score -= (occurrencesPerDay - 1) * 30;
                }
            }
        }
        return score;
    }

    private TimetableEntry requireTimetableEntry(Long entryId) {
        return timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> Objects.equals(entry.id(), entryId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intrarea din orar nu exista"));
    }

    private void ensureTimetableEntryVersion(TimetableEntry entry, Integer version) {
        if (!Objects.equals(entry.version(), version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Orarul a fost modificat intre timp. Da refresh si incearca din nou.");
        }
    }

    private TimetableEntry findClassTimetableEntry(Long classId, Integer weekday, Integer indexInDay, Long ignoredEntryId) {
        return timetablesByClassId.getOrDefault(classId, List.of()).stream()
                .filter(entry -> !Objects.equals(entry.id(), ignoredEntryId))
                .filter(entry -> Objects.equals(entry.weekday(), weekday) && Objects.equals(entry.indexInDay(), indexInDay))
                .findFirst()
                .orElse(null);
    }

    private TimetableMoveCandidate buildMoveCandidate(
            TimetableEntry source,
            TimetableEntry target,
            Integer targetWeekday,
            Integer targetIndexInDay,
            String requestedMode
    ) {
        if (Objects.equals(source.weekday(), targetWeekday) && Objects.equals(source.indexInDay(), targetIndexInDay)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ora este deja pe slotul selectat.");
        }

        String normalizedMode = normalizeMoveMode(requestedMode, target != null);
        Set<Long> ignoredEntryIds = target == null
                ? Set.of(source.id())
                : Set.of(source.id(), target.id());

        List<TimetableEntry> updatedEntries = new ArrayList<>();
        updatedEntries.add(resolveMovedEntry(source, targetWeekday, targetIndexInDay, ignoredEntryIds));
        if (target != null) {
            updatedEntries.add(resolveMovedEntry(target, source.weekday(), source.indexInDay(), ignoredEntryIds));
        }

        List<TimetableEntry> classEntries = replaceClassEntries(source.classId(), source.id(), target == null ? null : target.id(), updatedEntries);
        List<String> warnings = buildSoftWarnings(source.classId(), classEntries);
        return new TimetableMoveCandidate(updatedEntries, warnings, normalizedMode);
    }

    private String normalizeMoveMode(String requestedMode, boolean targetOccupied) {
        String normalizedMode = requestedMode == null ? "swap-auto" : requestedMode.trim().toLowerCase(Locale.ROOT);
        if (targetOccupied) {
            if (!"swap-auto".equals(normalizedMode)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slotul selectat este ocupat si necesita swap.");
            }
            return "swap-auto";
        }
        if (!"move".equals(normalizedMode) && !"swap-auto".equals(normalizedMode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modul de mutare este invalid.");
        }
        return "move";
    }

    private TimetableEntry resolveMovedEntry(
            TimetableEntry entry,
            Integer newWeekday,
            Integer newIndexInDay,
            Set<Long> ignoredEntryIds
    ) {
        String teacherUsername = resolveTeacherForSlot(entry, newWeekday, newIndexInDay, ignoredEntryIds);
        Room room = resolveRoomForSlot(entry, newWeekday, newIndexInDay, ignoredEntryIds);
        UserProfile teacher = getProfile(teacherUsername);

        return new TimetableEntry(
                entry.id(),
                entry.classId(),
                entry.className(),
                entry.subjectId(),
                entry.subjectName(),
                room.id(),
                room.name(),
                teacher.username(),
                teacher.firstName() + " " + teacher.lastName(),
                newWeekday,
                newIndexInDay,
                entry.version() + 1
        );
    }

    private String resolveTeacherForSlot(TimetableEntry entry, Integer weekday, Integer indexInDay, Set<Long> ignoredEntryIds) {
        String assignedTeacherUsername = assignedTeacherForClassSubject(entry.classId(), entry.subjectId(), ignoredEntryIds);
        if (assignedTeacherUsername != null) {
            try {
                return validateTeacherAvailability(assignedTeacherUsername, entry.subjectId(), ignoredEntryIds, weekday, indexInDay);
            } catch (ResponseStatusException ignored) {
            }
        }

        try {
            return validateTeacherAvailability(entry.teacherUsername(), entry.subjectId(), ignoredEntryIds, weekday, indexInDay);
        } catch (ResponseStatusException ignored) {
            return selectTeacherForSubject(entry.subjectId(), ignoredEntryIds, weekday, indexInDay);
        }
    }

    private Room resolveRoomForSlot(TimetableEntry entry, Integer weekday, Integer indexInDay, Set<Long> ignoredEntryIds) {
        if (entry.roomId() != null) {
            try {
                validateRoomAvailability(entry.roomId(), ignoredEntryIds, weekday, indexInDay);
                return requireRoom(entry.roomId());
            } catch (ResponseStatusException ignored) {
            }
        }
        return defaultRoomForSubject(entry.classId(), entry.subjectName(), weekday, indexInDay, ignoredEntryIds);
    }

    private void applyTimetableMove(Long classId, Long sourceEntryId, Long targetEntryId, List<TimetableEntry> updatedEntries) {
        List<TimetableEntry> replacedEntries = replaceClassEntries(classId, sourceEntryId, targetEntryId, updatedEntries);
        timetablesByClassId.put(classId, replacedEntries);
        updatedEntries.forEach(persistentStateService::saveTimetableEntry);
    }

    private List<TimetableEntry> replaceClassEntries(Long classId, Long sourceEntryId, Long targetEntryId, List<TimetableEntry> replacements) {
        List<TimetableEntry> currentEntries = timetablesByClassId.getOrDefault(classId, List.of());
        List<TimetableEntry> nextEntries = new ArrayList<>();
        for (TimetableEntry entry : currentEntries) {
            if (Objects.equals(entry.id(), sourceEntryId) || Objects.equals(entry.id(), targetEntryId)) {
                continue;
            }
            nextEntries.add(entry);
        }
        nextEntries.addAll(replacements);
        nextEntries.sort(Comparator.comparing(TimetableEntry::weekday).thenComparing(TimetableEntry::indexInDay));
        return nextEntries;
    }

    private List<String> buildSoftWarnings(Long classId, List<TimetableEntry> classEntries) {
        SchoolClass schoolClass = requireClass(classId);
        LinkedHashMap<String, Integer> subjectTargets = timetablePlanForClass(schoolClass);
        List<SlotAssignment> currentAssignments = toSlotAssignments(timetablesByClassId.getOrDefault(classId, List.of()));
        List<SlotAssignment> candidateAssignments = toSlotAssignments(classEntries);

        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        int currentScore = evaluateScheduleQuality(classId, subjectTargets, currentAssignments);
        int candidateScore = evaluateScheduleQuality(classId, subjectTargets, candidateAssignments);
        if (candidateScore < currentScore) {
            warnings.add("Mutarea reduce calitatea estimata a orarului pentru clasa selectata.");
        }

        for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
            int currentWeekday = weekday;
            List<SlotAssignment> dayAssignments = candidateAssignments.stream()
                    .filter(entry -> entry.slot().weekday() == currentWeekday)
                    .sorted(Comparator.comparingInt(entry -> entry.slot().indexInDay()))
                    .toList();

            for (int index = 0; index < dayAssignments.size(); index++) {
                SlotAssignment current = dayAssignments.get(index);
                String subjectName = subjectName(current);

                if (isHeavySubject(subjectName) && current.slot().indexInDay() >= 5) {
                    warnings.add(subjectName + " ajunge intr-un interval tarziu in " + weekdayLabel(currentWeekday) + ".");
                }
                if (index > 0 && Objects.equals(dayAssignments.get(index - 1).subjectId(), current.subjectId())) {
                    warnings.add(subjectName + " apare in ore consecutive in " + weekdayLabel(currentWeekday) + ".");
                }
            }
        }

        for (Map.Entry<String, Integer> target : subjectTargets.entrySet()) {
            if (target.getValue() > WEEK_DAYS) {
                continue;
            }
            for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
                int occurrences = countAssignmentsForSubjectOnDay(candidateAssignments, weekday, target.getKey());
                if (occurrences > 1) {
                    warnings.add(target.getKey() + " apare de " + occurrences + " ori in " + weekdayLabel(weekday) + ".");
                }
            }
        }

        int minDayLoad = Integer.MAX_VALUE;
        int maxDayLoad = Integer.MIN_VALUE;
        for (int weekday = 1; weekday <= WEEK_DAYS; weekday++) {
            int dayLoad = countAssignmentsForDay(candidateAssignments, weekday);
            minDayLoad = Math.min(minDayLoad, dayLoad);
            maxDayLoad = Math.max(maxDayLoad, dayLoad);
        }
        if (maxDayLoad - minDayLoad > 2) {
            warnings.add("Mutarea dezechilibreaza distributia orelor intre zile.");
        }

        return warnings.stream().toList();
    }

    private List<SlotAssignment> toSlotAssignments(List<TimetableEntry> entries) {
        return entries.stream()
                .map(entry -> new SlotAssignment(
                        new Slot(entry.weekday(), entry.indexInDay()),
                        entry.subjectId(),
                        entry.teacherUsername(),
                        entry.roomId()
                ))
                .toList();
    }

    private Set<Long> ignoredEntryIds(Long ignoredEntryId) {
        return ignoredEntryId == null ? Set.of() : Set.of(ignoredEntryId);
    }

    private String assignedTeacherForClassSubject(Long classId, Long subjectId, Long ignoredEntryId) {
        return assignedTeacherForClassSubject(classId, subjectId, ignoredEntryIds(ignoredEntryId));
    }

    private String assignedTeacherForClassSubject(Long classId, Long subjectId, Set<Long> ignoredEntryIds) {
        return timetablesByClassId.getOrDefault(classId, List.of()).stream()
                .filter(entry -> !ignoredEntryIds.contains(entry.id()))
                .filter(entry -> Objects.equals(entry.subjectId(), subjectId))
                .map(TimetableEntry::teacherUsername)
                .findFirst()
                .orElse(null);
    }
    private String validateTeacherAvailability(String teacherUsername, Long subjectId, Long ignoredEntryId, Integer weekday, Integer indexInDay) {
        return validateTeacherAvailability(teacherUsername, subjectId, ignoredEntryIds(ignoredEntryId), weekday, indexInDay);
    }

    private String validateTeacherAvailability(String teacherUsername, Long subjectId, Set<Long> ignoredEntryIds, Integer weekday, Integer indexInDay) {
        if (teacherUsername == null || teacherUsername.isBlank()) {
            return selectTeacherForSubject(subjectId, ignoredEntryIds, weekday, indexInDay);
        }
        if (!teachersBySubjectId.getOrDefault(subjectId, List.of()).contains(teacherUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profesorul selectat nu poate preda materia aleasa.");
        }
        boolean conflict = timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> !ignoredEntryIds.contains(entry.id()))
                .anyMatch(entry -> teacherUsername.equals(entry.teacherUsername())
                        && Objects.equals(entry.weekday(), weekday)
                        && Objects.equals(entry.indexInDay(), indexInDay));
        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profesorul este deja ocupat in acest interval. Da refresh si alege alta varianta.");
        }
        return teacherUsername;
    }

    private String selectTeacherForSubject(Long subjectId, Long ignoredEntryId, Integer weekday, Integer indexInDay) {
        return selectTeacherForSubject(subjectId, ignoredEntryIds(ignoredEntryId), weekday, indexInDay);
    }

    private String selectTeacherForSubject(Long subjectId, Set<Long> ignoredEntryIds, Integer weekday, Integer indexInDay) {
        return teachersBySubjectId.getOrDefault(subjectId, List.of()).stream()
                .filter(username -> timetablesByClassId.values().stream()
                        .flatMap(Collection::stream)
                        .filter(entry -> !ignoredEntryIds.contains(entry.id()))
                        .noneMatch(entry -> username.equals(entry.teacherUsername())
                                && Objects.equals(entry.weekday(), weekday)
                                && Objects.equals(entry.indexInDay(), indexInDay)))
                .min(Comparator.comparingInt(this::existingTeacherLoad).thenComparing(String::compareTo))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Nu exista profesor disponibil pentru materia selectata in acest interval."));
    }

    private void validateRoomAvailability(Long roomId, Long ignoredEntryId, Integer weekday, Integer indexInDay) {
        validateRoomAvailability(roomId, ignoredEntryIds(ignoredEntryId), weekday, indexInDay);
    }

    private void validateRoomAvailability(Long roomId, Set<Long> ignoredEntryIds, Integer weekday, Integer indexInDay) {
        boolean conflict = timetablesByClassId.values().stream()
                .flatMap(Collection::stream)
                .filter(entry -> !ignoredEntryIds.contains(entry.id()))
                .anyMatch(entry -> Objects.equals(entry.roomId(), roomId)
                        && Objects.equals(entry.weekday(), weekday)
                        && Objects.equals(entry.indexInDay(), indexInDay));
        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sala este deja ocupata in acest interval. Da refresh si alege alta sala.");
        }
    }

    private Room defaultRoomForSubject(Long classId, String subjectName, Integer weekday, Integer indexInDay, Long ignoredEntryId) {
        return defaultRoomForSubject(classId, subjectName, weekday, indexInDay, ignoredEntryIds(ignoredEntryId));
    }

    private Room defaultRoomForSubject(Long classId, String subjectName, Integer weekday, Integer indexInDay, Set<Long> ignoredEntryIds) {
        for (Long roomId : candidateRoomIds(classId, subjectName)) {
            boolean conflict = timetablesByClassId.values().stream()
                    .flatMap(Collection::stream)
                    .filter(entry -> !ignoredEntryIds.contains(entry.id()))
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

    private boolean prefersMiddayForProfile(Long classId, String subjectName) {
        if (subjectName == null) {
            return false;
        }
        SchoolClass schoolClass = classes.get(classId);
        if (schoolClass == null || schoolClass.profile() == null) {
            return false;
        }
        return switch (schoolClass.profile()) {
            case "Filologie" -> FILOLOGIE_PROFILE_SUBJECTS.contains(subjectName);
            case "Matematica-Informatica", "Matematica-Informatica Intensiv" -> STEM_PROFILE_SUBJECTS.contains(subjectName);
            default -> false;
        };
    }

    private boolean usesPreferredHomeRoom(Long classId, String subjectName, Long roomId) {
        return !requiresSpecialRoom(subjectName) && Objects.equals(homeRoomIdsByClassId.get(classId), roomId);
    }

    private boolean requiresSpecialRoom(String subjectName) {
        return Set.of("Informatica", "Informatica intensiv", "TIC", "Fizica", "Chimie", "Educatie fizica").contains(subjectName);
    }

    private String subjectName(SlotAssignment assignment) {
        return requireSubject(assignment.subjectId()).name();
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
            classes.put(classId, new SchoolClass(classId, CLASS_NAMES[index], CLASS_PROFILES[index], null, null));
        }
    }

    private void updateHomeroomTeacherAssignment(UserProfile previousProfile, UserProfile updatedProfile, Long homeroomClassId) {
        if (!"professor".equals(updatedProfile.role())) {
            return;
        }

        SchoolClass currentHomeroomClass = homeroomClassForTeacher(updatedProfile.username());
        SchoolClass requestedClass = homeroomClassId == null ? null : requireClass(homeroomClassId);

        if (requestedClass != null) {
            if (!teachesClass(updatedProfile.username(), homeroomClassId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dirigintele trebuie sa predea la clasa selectata");
            }
            if (requestedClass.homeroomTeacherUsername() != null && !updatedProfile.username().equals(requestedClass.homeroomTeacherUsername())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Clasa are deja un alt diriginte");
            }
        }

        if (currentHomeroomClass != null && !Objects.equals(currentHomeroomClass.id(), homeroomClassId)) {
            saveSchoolClass(new SchoolClass(
                    currentHomeroomClass.id(),
                    currentHomeroomClass.name(),
                    currentHomeroomClass.profile(),
                    null,
                    null
            ));
        }

        if (requestedClass != null) {
            saveSchoolClass(new SchoolClass(
                    requestedClass.id(),
                    requestedClass.name(),
                    requestedClass.profile(),
                    updatedProfile.username(),
                    displayName(updatedProfile)
            ));
        }
    }

    private void saveSchoolClass(SchoolClass schoolClass) {
        classes.put(schoolClass.id(), schoolClass);
        referenceDataPersistenceService.saveSchoolClass(schoolClass);
    }

    private SchoolClass homeroomClassForTeacher(String teacherUsername) {
        return classes.values().stream()
                .filter(schoolClass -> teacherUsername.equals(schoolClass.homeroomTeacherUsername()))
                .findFirst()
                .orElse(null);
    }

    private void reconcileHomeroomAssignmentsWithTimetables() {
        classes.values().stream()
                .map(SchoolClass::id)
                .sorted()
                .forEach(this::reconcileHomeroomAssignmentForClass);
    }

    private void reconcileHomeroomAssignmentForClass(Long classId) {
        SchoolClass schoolClass = requireClass(classId);
        String homeroomTeacherUsername = schoolClass.homeroomTeacherUsername();
        if (homeroomTeacherUsername == null || homeroomTeacherUsername.isBlank()) {
            return;
        }
        if (teachesClass(homeroomTeacherUsername, classId)) {
            return;
        }

        saveSchoolClass(new SchoolClass(
                schoolClass.id(),
                schoolClass.name(),
                schoolClass.profile(),
                null,
                null
        ));
    }

    private boolean teachesClass(String teacherUsername, Long classId) {
        return getTimetableForClass(classId).stream()
                .anyMatch(entry -> teacherUsername.equals(entry.teacherUsername()));
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
        Random identityRandom = new Random(STUDENT_IDENTITY_RANDOM_SEED);
        Set<String> usedAddresses = new LinkedHashSet<>();
        Set<String> usedCnps = new LinkedHashSet<>();
        Set<String> usedIdentityDocumentKeys = new LinkedHashSet<>();

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
            String address = generateUniqueStudentAddress(usedAddresses, identityRandom);
            String cnp = generateUniqueStudentCnp(usedCnps, identityRandom, schoolClass.name());
            StudentIdentityDocument identityDocument = generateUniqueStudentIdentityDocument(usedIdentityDocumentKeys, identityRandom);
            profilesByUsername.put(username, new UserProfile(
                    profileIds.getAndIncrement(),
                    1,
                    username,
                    "student",
                    firstName,
                    lastName,
                    username + "@timetable.local",
                    address,
                    cnp,
                    identityDocument.series(),
                    identityDocument.serialNumber(),
                    generateStudentFatherInitial(identityRandom),
                    classId,
                    schoolClass.name(),
                    List.of()
            ));
        }
    }

    private void addStaffProfile(String username, String role, String firstName, String lastName) {
        profilesByUsername.put(username, new UserProfile(
                profileIds.getAndIncrement(),
                1,
                username,
                role,
                firstName,
                lastName,
                username + "@timetable.local",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        ));
    }

    private void addTeacherProfile(TeacherSeed teacher) {
        profilesByUsername.put(teacher.username(), new UserProfile(
                profileIds.getAndIncrement(),
                1,
                teacher.username(),
                "professor",
                teacher.firstName(),
                teacher.lastName(),
                teacher.username() + "@timetable.local",
                null,
                null,
                null,
                null,
                null,
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
                new TeacherSeed("mate01", "Cristian", "Serban", "Matematica"),
                new TeacherSeed("mate02", "Irina", "Voicu", "Matematica"),
                new TeacherSeed("sport01", "Dorin", "Avram", "Educatie fizica"),
                new TeacherSeed("chimie01", "Alina", "Marin", "Chimie"),
                new TeacherSeed("fizica01", "Mircea", "Petrescu", "Fizica"),
                new TeacherSeed("fizica02", "Anca", "Stan", "Fizica"),
                new TeacherSeed("biologie01", "Laura", "Nistor", "Biologie"),
                new TeacherSeed("engleza01", "Simona", "Manole", "Limba engleza"),
                new TeacherSeed("engleza02", "Monica", "Diaconescu", "Limba engleza"),
                new TeacherSeed("franceza01", "Lavinia", "Coman", "Limba franceza"),
                new TeacherSeed("franceza02", "Mirela", "Ene", "Limba franceza"),
                new TeacherSeed("latina01", "Carmen", "Preda", "Limba latina"),
                new TeacherSeed("istorie01", "Dan", "Neagu", "Istorie"),
                new TeacherSeed("geografie01", "Claudiu", "Barbu", "Geografie"),
                new TeacherSeed("socioumane01", "Andrada", "Lazar", "Socio-umane"),
                new TeacherSeed("religie01", "Gabriel", "Constantin", "Religie"),
                new TeacherSeed("artistica01", "Diana", "Rosu", "Educatie artistica"),
                new TeacherSeed("tic01", "Bogdan", "Georgescu", "TIC"),
                new TeacherSeed("info01", "Marian", "Radu", "Informatica"),
                new TeacherSeed("infoint01", "Catalin", "Tudose", "Informatica intensiv"),
                new TeacherSeed("antreprenoriala01", "Iulia", "Sandu", "Educatie antreprenoriala"),
                new TeacherSeed("literatura01", "Sabina", "Matei", "Literatura universala"),
                new TeacherSeed("stiinte01", "Violeta", "Enache", "Stiinte")
        );
    }

    private void backfillMissingStudentIdentityData() {
        Set<String> usedAddresses = usedStudentAddresses();
        Set<String> usedCnps = usedStudentCnps();
        Set<String> usedIdentityDocumentKeys = usedStudentIdentityDocumentKeys();
        Random identityRandom = new Random(STUDENT_IDENTITY_RANDOM_SEED);
        List<UserProfile> updates = new ArrayList<>();

        for (UserProfile profile : getUserProfilesByRole("student")) {
            String address = profile.address();
            String cnp = profile.cnp();
            String idSeries = profile.idSeries();
            String serialNumber = profile.serialNumber();
            String fatherInitial = profile.fatherInitial();
            boolean changed = false;

            if (address == null || address.isBlank()) {
                address = generateUniqueStudentAddress(usedAddresses, identityRandom);
                changed = true;
            }
            if (cnp == null || cnp.isBlank() || !hasPreferredGeneratedStudentCnpFormat(cnp)) {
                cnp = generateUniqueStudentCnp(usedCnps, identityRandom, profile.className());
                changed = true;
            }
            if (!hasValidStudentIdentityDocument(idSeries, serialNumber)) {
                StudentIdentityDocument identityDocument = generateUniqueStudentIdentityDocument(usedIdentityDocumentKeys, identityRandom);
                idSeries = identityDocument.series();
                serialNumber = identityDocument.serialNumber();
                changed = true;
            }
            if (!hasValidStudentFatherInitial(fatherInitial)) {
                fatherInitial = generateStudentFatherInitial(identityRandom);
                changed = true;
            }

            if (changed) {
                updates.add(new UserProfile(
                        profile.id(),
                        profile.version() == null ? 1 : profile.version(),
                        profile.username(),
                        profile.role(),
                        profile.firstName(),
                        profile.lastName(),
                        profile.email(),
                        address,
                        cnp,
                        idSeries,
                        serialNumber,
                        fatherInitial,
                        profile.classId(),
                        profile.className(),
                        profile.subjectsTaught()
                ));
            }
        }

        for (UserProfile updated : updates) {
            profilesByUsername.put(updated.username(), updated);
            referenceDataPersistenceService.saveUserProfile(updated);
        }
    }

    private void synchronizeTeacherDisplayName(UserProfile previousProfile, UserProfile updatedProfile) {
        if (!"professor".equals(updatedProfile.role())) {
            return;
        }
        String previousTeacherName = displayName(previousProfile);
        String updatedTeacherName = displayName(updatedProfile);
        if (Objects.equals(previousTeacherName, updatedTeacherName)) {
            return;
        }

        for (List<TimetableEntry> entries : timetablesByClassId.values()) {
            for (int index = 0; index < entries.size(); index++) {
                TimetableEntry entry = entries.get(index);
                if (!updatedProfile.username().equals(entry.teacherUsername())) {
                    continue;
                }
                TimetableEntry updatedEntry = new TimetableEntry(
                        entry.id(),
                        entry.classId(),
                        entry.className(),
                        entry.subjectId(),
                        entry.subjectName(),
                        entry.roomId(),
                        entry.roomName(),
                        entry.teacherUsername(),
                        updatedTeacherName,
                        entry.weekday(),
                        entry.indexInDay(),
                        entry.version()
                );
                entries.set(index, updatedEntry);
                persistentStateService.saveTimetableEntry(updatedEntry);
            }
        }
    }

    private Set<String> usedStudentAddresses() {
        return profilesByUsername.values().stream()
                .filter(profile -> "student".equals(profile.role()))
                .map(UserProfile::address)
                .filter(address -> address != null && !address.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private Set<String> usedStudentCnps() {
        return profilesByUsername.values().stream()
                .filter(profile -> "student".equals(profile.role()))
                .map(UserProfile::cnp)
                .filter(cnp -> cnp != null && !cnp.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private Set<String> usedStudentIdentityDocumentKeys() {
        return profilesByUsername.values().stream()
                .filter(profile -> "student".equals(profile.role()))
                .filter(profile -> hasValidStudentIdentityDocument(profile.idSeries(), profile.serialNumber()))
                .map(profile -> identityDocumentKey(profile.idSeries(), profile.serialNumber()))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private String generateUniqueStudentAddress(Set<String> usedAddresses, Random random) {
        for (int attempt = 0; attempt < 2000; attempt++) {
            String street = STREET_NAMES[random.nextInt(STREET_NAMES.length)];
            int number = 1 + random.nextInt(180);
            String candidate;
            if (random.nextBoolean()) {
                candidate = "Str. " + street + " nr. " + number + ", " + STUDENT_CITY + ", " + STUDENT_COUNTY;
            } else {
                String block = BLOCK_NAMES[random.nextInt(BLOCK_NAMES.length)];
                int staircase = 1 + random.nextInt(4);
                int apartment = 1 + random.nextInt(40);
                candidate = "Str. " + street + " nr. " + number + ", bl. " + block + ", sc. " + staircase + ", ap. " + apartment + ", " + STUDENT_CITY + ", " + STUDENT_COUNTY;
            }
            if (usedAddresses.add(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut genera o adresa unica pentru elev");
    }

    private String generateUniqueStudentCnp(Set<String> usedCnps, Random random, String className) {
        int birthYear = birthYearForClass(className);
        for (int attempt = 0; attempt < 4000; attempt++) {
            int month = 1 + random.nextInt(12);
            int day = 1 + random.nextInt(LocalDate.of(birthYear, month, 1).lengthOfMonth());
            int sexDigit = random.nextBoolean() ? 5 : 6;
            int serial = 1 + random.nextInt(9);
            String base = String.format(Locale.ROOT, "%d%02d%02d%02d%02d00%d", sexDigit, birthYear % 100, month, day, ARGES_COUNTY_CODE, serial);
            String candidate = base + computeCnpControlDigit(base);
            if (usedCnps.add(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut genera un CNP unic pentru elev");
    }

    private StudentIdentityDocument generateUniqueStudentIdentityDocument(Set<String> usedIdentityDocumentKeys, Random random) {
        for (int attempt = 0; attempt < 4000; attempt++) {
            String series = ""
                    + ID_SERIES_LETTERS.charAt(random.nextInt(ID_SERIES_LETTERS.length()))
                    + ID_SERIES_LETTERS.charAt(random.nextInt(ID_SERIES_LETTERS.length()));
            String serialNumber = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            String candidateKey = identityDocumentKey(series, serialNumber);
            if (usedIdentityDocumentKeys.add(candidateKey)) {
                return new StudentIdentityDocument(series, serialNumber);
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nu s-a putut genera o combinatie unica de serie si numar pentru elev");
    }

    private String generateStudentFatherInitial(Random random) {
        return String.valueOf(ID_SERIES_LETTERS.charAt(random.nextInt(ID_SERIES_LETTERS.length())));
    }

    private boolean hasPreferredGeneratedStudentCnpFormat(String cnp) {
        return cnp != null && cnp.matches("[56]\\d{6}0300\\d\\d");
    }

    private boolean hasValidStudentIdentityDocument(String idSeries, String serialNumber) {
        return idSeries != null
                && idSeries.matches("[A-Z]{2}")
                && serialNumber != null
                && serialNumber.matches("\\d{6}");
    }

    private boolean hasValidStudentFatherInitial(String fatherInitial) {
        return fatherInitial != null && fatherInitial.matches("[A-Z]");
    }

    private String identityDocumentKey(String idSeries, String serialNumber) {
        return idSeries + ":" + serialNumber;
    }

    private int birthYearForClass(String className) {
        if (className == null || className.isBlank()) {
            return 2010;
        }
        String level = className.trim().split("\\s+")[0];
        return switch (level) {
            case "IX" -> 2010;
            case "X" -> 2009;
            case "XI" -> 2008;
            case "XII" -> 2007;
            default -> 2010;
        };
    }

    private int computeCnpControlDigit(String base) {
        int sum = 0;
        for (int index = 0; index < base.length(); index++) {
            sum += Character.getNumericValue(base.charAt(index)) * Character.getNumericValue(CNP_CONTROL_KEY.charAt(index));
        }
        int remainder = sum % 11;
        return remainder == 10 ? 1 : remainder;
    }

    private void validateCnp(String cnp) {
        if (cnp == null || !cnp.matches("\\d{13}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP invalid");
        }

        int sexDigit = Character.getNumericValue(cnp.charAt(0));
        int countyCode = Integer.parseInt(cnp.substring(7, 9));
        int serial = Integer.parseInt(cnp.substring(9, 12));
        int controlDigit = Character.getNumericValue(cnp.charAt(12));
        String base = cnp.substring(0, 12);

        if (sexDigit < 1 || sexDigit > 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP invalid");
        }
        if ((countyCode < 1 || countyCode > 52) && countyCode != 99) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP invalid");
        }
        if (serial < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP invalid");
        }
        if (computeCnpControlDigit(base) != controlDigit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP invalid");
        }

        int century = switch (sexDigit) {
            case 1, 2 -> 1900;
            case 3, 4 -> 1800;
            case 5, 6, 7, 8 -> 2000;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP invalid");
        };

        int year = century + Integer.parseInt(cnp.substring(1, 3));
        int month = Integer.parseInt(cnp.substring(3, 5));
        int day = Integer.parseInt(cnp.substring(5, 7));
        try {
            LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNP invalid");
        }
    }

    private String normalizeRequiredProfileField(String value, String errorMessage) {
        String normalized = normalizeOptionalProfileField(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    private String normalizeUsername(String value, String errorMessage) {
        return normalizeRequiredProfileField(value, errorMessage).toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalProfileField(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStudentIdentitySeries(String value) {
        String normalized = normalizeOptionalProfileField(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeStudentIdentitySerialNumber(String value) {
        return normalizeOptionalProfileField(value);
    }

    private String normalizeStudentFatherInitial(String value) {
        String normalized = normalizeOptionalProfileField(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeSubjectNames(List<String> subjectsTaught) {
        if (subjectsTaught == null || subjectsTaught.isEmpty()) {
            return List.of();
        }

        List<String> normalized = subjectsTaught.stream()
                .map(this::normalizeOptionalProfileField)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        for (String subjectName : normalized) {
            if (!subjectIdsByName.containsKey(subjectName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Materia " + subjectName + " nu exista");
            }
        }

        return normalized;
    }

    private Optional<UserProfile> findProfileByUsername(String username) {
        String normalized = normalizeOptionalProfileField(username);
        if (normalized == null) {
            return Optional.empty();
        }

        UserProfile exactMatch = profilesByUsername.get(normalized);
        if (exactMatch != null) {
            return Optional.of(exactMatch);
        }

        return profilesByUsername.values().stream()
                .filter(profile -> normalized.equalsIgnoreCase(profile.username()))
                .findFirst();
    }

    private Optional<UserProfile> findProfileByEmail(String email) {
        String normalized = normalizeOptionalProfileField(email);
        if (normalized == null) {
            return Optional.empty();
        }

        return profilesByUsername.values().stream()
                .filter(profile -> normalized.equalsIgnoreCase(profile.email()))
                .findFirst();
    }

    private String displayName(UserProfile profile) {
        return profile.firstName() + " " + profile.lastName();
    }

    private boolean hasAnyRole(List<String> roles, String... allowedRoles) {
        for (String allowedRole : allowedRoles) {
            if (roles.contains(allowedRole)) {
                return true;
            }
        }
        return false;
    }
}














