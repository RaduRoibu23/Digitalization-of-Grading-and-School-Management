package ro.timetable.service;

import jakarta.annotation.PostConstruct;
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

import java.util.List;
import java.util.Map;

@Service
public class ReferenceDataPersistenceService {

    private final SchoolDataService schoolDataService;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final RoomRepository roomRepository;
    private final UserProfileRepository userProfileRepository;

    public ReferenceDataPersistenceService(
            SchoolDataService schoolDataService,
            SchoolClassRepository schoolClassRepository,
            SubjectRepository subjectRepository,
            RoomRepository roomRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.schoolDataService = schoolDataService;
        this.schoolClassRepository = schoolClassRepository;
        this.subjectRepository = subjectRepository;
        this.roomRepository = roomRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @PostConstruct
    @Transactional
    void seedReferenceData() {
        if (schoolClassRepository.count() == 0) {
            schoolClassRepository.saveAll(schoolDataService.getClasses().stream().map(this::toEntity).toList());
        }
        if (subjectRepository.count() == 0) {
            subjectRepository.saveAll(schoolDataService.getSubjects().stream().map(this::toEntity).toList());
        }
        if (roomRepository.count() == 0) {
            roomRepository.saveAll(schoolDataService.getRooms().stream().map(this::toEntity).toList());
        }
        if (userProfileRepository.count() == 0) {
            userProfileRepository.saveAll(schoolDataService.getProfilesByRole(null).stream().map(this::mapToUserProfile).map(this::toEntity).toList());
        }
    }

    @Transactional
    public void saveUserProfile(UserProfile profile) {
        userProfileRepository.save(toEntity(profile));
    }

    private UserProfile mapToUserProfile(Map<String, Object> source) {
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
