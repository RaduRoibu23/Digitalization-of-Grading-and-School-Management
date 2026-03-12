package ro.timetable.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.timetable.model.Room;
import ro.timetable.model.SchoolClass;
import ro.timetable.model.Subject;
import ro.timetable.model.UserProfile;
import ro.timetable.persistence.RoomEntity;
import ro.timetable.persistence.RoomRepository;
import ro.timetable.persistence.SchoolClassEntity;
import ro.timetable.persistence.SchoolClassRepository;
import ro.timetable.persistence.SubjectEntity;
import ro.timetable.persistence.SubjectRepository;
import ro.timetable.persistence.UserProfileEntity;
import ro.timetable.persistence.UserProfileRepository;

import java.util.Comparator;
import java.util.List;

@Service
public class ReferenceDataPersistenceService {

    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final RoomRepository roomRepository;
    private final UserProfileRepository userProfileRepository;

    public ReferenceDataPersistenceService(
            SchoolClassRepository schoolClassRepository,
            SubjectRepository subjectRepository,
            RoomRepository roomRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.schoolClassRepository = schoolClassRepository;
        this.subjectRepository = subjectRepository;
        this.roomRepository = roomRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasReferenceData() {
        return schoolClassRepository.count() > 0
                && subjectRepository.count() > 0
                && roomRepository.count() > 0
                && userProfileRepository.count() > 0;
    }

    @Transactional
    public void saveReferenceData(List<SchoolClass> classes, List<Subject> subjects, List<Room> rooms, List<UserProfile> profiles) {
        schoolClassRepository.saveAll(classes.stream().map(this::toEntity).toList());
        subjectRepository.saveAll(subjects.stream().map(this::toEntity).toList());
        roomRepository.saveAll(rooms.stream().map(this::toEntity).toList());
        userProfileRepository.saveAll(profiles.stream().map(this::toEntity).toList());
    }

    @Transactional(readOnly = true)
    public List<SchoolClass> loadClasses() {
        return schoolClassRepository.findAll().stream()
                .sorted(Comparator.comparing(SchoolClassEntity::getId))
                .map(entity -> new SchoolClass(entity.getId(), entity.getName(), entity.getProfile()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Subject> loadSubjects() {
        return subjectRepository.findAll().stream()
                .sorted(Comparator.comparing(SubjectEntity::getId))
                .map(entity -> new Subject(entity.getId(), entity.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Room> loadRooms() {
        return roomRepository.findAll().stream()
                .sorted(Comparator.comparing(RoomEntity::getId))
                .map(entity -> new Room(entity.getId(), entity.getName(), entity.getCapacity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserProfile> loadProfiles() {
        return userProfileRepository.findAll().stream()
                .sorted(Comparator.comparing(UserProfileEntity::getId))
                .map(this::toModel)
                .toList();
    }

    @Transactional
    public void saveUserProfile(UserProfile profile) {
        userProfileRepository.save(toEntity(profile));
    }

    private UserProfile toModel(UserProfileEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getUsername(),
                entity.getRole(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getEmail(),
                entity.getSchoolClass() == null ? null : entity.getSchoolClass().getId(),
                entity.getSchoolClass() == null ? null : entity.getSchoolClass().getName(),
                entity.getSubjectsTaught() == null ? List.of() : List.copyOf(entity.getSubjectsTaught())
        );
    }

    private SchoolClassEntity toEntity(SchoolClass schoolClass) {
        SchoolClassEntity entity = new SchoolClassEntity();
        entity.setId(schoolClass.id());
        entity.setName(schoolClass.name());
        entity.setProfile(schoolClass.profile());
        return entity;
    }

    private SubjectEntity toEntity(Subject subject) {
        SubjectEntity entity = new SubjectEntity();
        entity.setId(subject.id());
        entity.setName(subject.name());
        return entity;
    }

    private RoomEntity toEntity(Room room) {
        RoomEntity entity = new RoomEntity();
        entity.setId(room.id());
        entity.setName(room.name());
        entity.setCapacity(room.capacity());
        return entity;
    }

    private UserProfileEntity toEntity(UserProfile profile) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setId(profile.id());
        entity.setUsername(profile.username());
        entity.setRole(profile.role());
        entity.setFirstName(profile.firstName());
        entity.setLastName(profile.lastName());
        entity.setEmail(profile.email());
        entity.setSubjectsTaught(profile.subjectsTaught());
        if (profile.classId() != null) {
            entity.setSchoolClass(schoolClassRepository.getReferenceById(profile.classId()));
        }
        return entity;
    }
}
