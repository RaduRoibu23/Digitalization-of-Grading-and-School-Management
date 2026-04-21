package ro.timetable.common.util;

import java.util.List;
import ro.timetable.catalog.entity.GradeChangeRequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.timetable.catalog.entity.StudentAbsenceEntity;
import ro.timetable.catalog.entity.StudentGradeEntity;
import ro.timetable.catalog.model.GradeChangeRequest;
import ro.timetable.catalog.model.StudentAbsence;
import ro.timetable.catalog.model.StudentGrade;
import ro.timetable.catalog.repository.GradeChangeRequestRepository;
import ro.timetable.catalog.repository.StudentAbsenceRepository;
import ro.timetable.catalog.repository.StudentGradeRepository;
import ro.timetable.timetable.entity.TimetableEntryEntity;
import ro.timetable.timetable.model.TimetableEntry;
import ro.timetable.timetable.repository.TimetableEntryRepository;

@Service
public class PersistentStateService {

    private final TimetableEntryRepository timetableEntryRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final StudentAbsenceRepository studentAbsenceRepository;
    private final GradeChangeRequestRepository gradeChangeRequestRepository;

    public PersistentStateService(
            TimetableEntryRepository timetableEntryRepository,
            StudentGradeRepository studentGradeRepository,
            StudentAbsenceRepository studentAbsenceRepository,
            GradeChangeRequestRepository gradeChangeRequestRepository
    ) {
        this.timetableEntryRepository = timetableEntryRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.studentAbsenceRepository = studentAbsenceRepository;
        this.gradeChangeRequestRepository = gradeChangeRequestRepository;
    }

    public List<TimetableEntry> loadTimetableEntries() {
        return timetableEntryRepository.findAllByOrderByClassIdAscWeekdayAscIndexInDayAsc().stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional
    public void replaceTimetableForClass(Long classId, List<TimetableEntry> entries) {
        timetableEntryRepository.deleteByClassId(classId);
        timetableEntryRepository.saveAll(entries.stream().map(this::toEntity).toList());
    }

    @Transactional
    public void saveTimetableEntry(TimetableEntry entry) {
        timetableEntryRepository.save(toEntity(entry));
    }

    @Transactional
    public void deleteTimetable(Long classId) {
        timetableEntryRepository.deleteByClassId(classId);
    }

    public List<StudentGrade> loadGrades() {
        return studentGradeRepository.findAllByOrderByStudentUsernameAscSubjectNameAscGradeDateDescIdDesc().stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional
    public void saveGrade(StudentGrade grade) {
        studentGradeRepository.save(toEntity(grade));
    }

    @Transactional
    public void saveGrades(List<StudentGrade> grades) {
        if (grades == null || grades.isEmpty()) {
            return;
        }
        studentGradeRepository.saveAll(grades.stream().map(this::toEntity).toList());
    }

    @Transactional
    public void deleteGrade(Long gradeId) {
        studentGradeRepository.deleteById(gradeId);
    }

    public List<StudentAbsence> loadAbsences() {
        return studentAbsenceRepository.findAllByOrderByStudentUsernameAscSubjectNameAscAbsenceDateDescIdDesc().stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional
    public void saveAbsence(StudentAbsence absence) {
        studentAbsenceRepository.save(toEntity(absence));
    }

    @Transactional
    public void saveAbsences(List<StudentAbsence> absences) {
        if (absences == null || absences.isEmpty()) {
            return;
        }
        studentAbsenceRepository.saveAll(absences.stream().map(this::toEntity).toList());
    }

    public List<GradeChangeRequest> loadGradeChangeRequests() {
        return gradeChangeRequestRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional
    public void saveGradeChangeRequest(GradeChangeRequest request) {
        gradeChangeRequestRepository.save(toEntity(request));
    }

    @Transactional
    public void saveGradeChangeRequests(List<GradeChangeRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        gradeChangeRequestRepository.saveAll(requests.stream().map(this::toEntity).toList());
    }

    private TimetableEntry toModel(TimetableEntryEntity entity) {
        return new TimetableEntry(
                entity.getId(),
                entity.getClassId(),
                entity.getClassName(),
                entity.getSubjectId(),
                entity.getSubjectName(),
                entity.getRoomId(),
                entity.getRoomName(),
                entity.getTeacherUsername(),
                entity.getTeacherName(),
                entity.getWeekday(),
                entity.getIndexInDay(),
                entity.getVersion()
        );
    }

    private TimetableEntryEntity toEntity(TimetableEntry entry) {
        TimetableEntryEntity entity = new TimetableEntryEntity();
        entity.setId(entry.id());
        entity.setClassId(entry.classId());
        entity.setClassName(entry.className());
        entity.setSubjectId(entry.subjectId());
        entity.setSubjectName(entry.subjectName());
        entity.setRoomId(entry.roomId());
        entity.setRoomName(entry.roomName());
        entity.setTeacherUsername(entry.teacherUsername());
        entity.setTeacherName(entry.teacherName());
        entity.setWeekday(entry.weekday());
        entity.setIndexInDay(entry.indexInDay());
        entity.setVersion(entry.version());
        return entity;
    }

    private StudentGrade toModel(StudentGradeEntity entity) {
        return new StudentGrade(
                entity.getId(),
                entity.getStudentUsername(),
                entity.getStudentName(),
                entity.getClassId(),
                entity.getClassName(),
                entity.getSubjectId(),
                entity.getSubjectName(),
                entity.getGradeValue(),
                entity.getGradeDate(),
                entity.getTeacherUsername(),
                entity.getTeacherName(),
                entity.getComment(),
                entity.getVersion()
        );
    }

    private StudentGradeEntity toEntity(StudentGrade grade) {
        StudentGradeEntity entity = new StudentGradeEntity();
        entity.setId(grade.id());
        entity.setStudentUsername(grade.studentUsername());
        entity.setStudentName(grade.studentName());
        entity.setClassId(grade.classId());
        entity.setClassName(grade.className());
        entity.setSubjectId(grade.subjectId());
        entity.setSubjectName(grade.subjectName());
        entity.setGradeValue(grade.gradeValue());
        entity.setGradeDate(grade.gradeDate());
        entity.setTeacherUsername(grade.teacherUsername());
        entity.setTeacherName(grade.teacherName());
        entity.setComment(grade.comment());
        entity.setVersion(grade.version());
        return entity;
    }

    private StudentAbsence toModel(StudentAbsenceEntity entity) {
        return new StudentAbsence(
                entity.getId(),
                entity.getStudentUsername(),
                entity.getStudentName(),
                entity.getClassId(),
                entity.getClassName(),
                entity.getSubjectId(),
                entity.getSubjectName(),
                entity.getAbsenceDate(),
                entity.getTeacherUsername(),
                entity.getTeacherName(),
                entity.isMotivated(),
                entity.getMotivatedByUsername(),
                entity.getMotivatedByName(),
                entity.getMotivatedAt(),
                entity.getMotivationReason(),
                entity.getVersion()
        );
    }

    private StudentAbsenceEntity toEntity(StudentAbsence absence) {
        StudentAbsenceEntity entity = new StudentAbsenceEntity();
        entity.setId(absence.id());
        entity.setStudentUsername(absence.studentUsername());
        entity.setStudentName(absence.studentName());
        entity.setClassId(absence.classId());
        entity.setClassName(absence.className());
        entity.setSubjectId(absence.subjectId());
        entity.setSubjectName(absence.subjectName());
        entity.setAbsenceDate(absence.absenceDate());
        entity.setTeacherUsername(absence.teacherUsername());
        entity.setTeacherName(absence.teacherName());
        entity.setMotivated(absence.motivated());
        entity.setMotivatedByUsername(absence.motivatedByUsername());
        entity.setMotivatedByName(absence.motivatedByName());
        entity.setMotivatedAt(absence.motivatedAt());
        entity.setMotivationReason(absence.motivationReason());
        entity.setVersion(absence.version());
        return entity;
    }

    private GradeChangeRequest toModel(GradeChangeRequestEntity entity) {
        return new GradeChangeRequest(
                entity.getId(),
                entity.getGradeId(),
                entity.getRequestType(),
                entity.getStatus(),
                entity.getBaseGradeVersion(),
                entity.getProposedGradeValue(),
                entity.getProposedGradeDate(),
                entity.getProposedComment(),
                entity.getReason(),
                entity.getRequestedByUsername(),
                entity.getReviewedByUsername(),
                entity.getResolutionNote(),
                entity.getCreatedAt(),
                entity.getReviewedAt()
        );
    }

    private GradeChangeRequestEntity toEntity(GradeChangeRequest request) {
        GradeChangeRequestEntity entity = new GradeChangeRequestEntity();
        entity.setId(request.id());
        entity.setGradeId(request.gradeId());
        entity.setRequestType(request.requestType());
        entity.setStatus(request.status());
        entity.setBaseGradeVersion(request.baseGradeVersion());
        entity.setProposedGradeValue(request.proposedGradeValue());
        entity.setProposedGradeDate(request.proposedGradeDate());
        entity.setProposedComment(request.proposedComment());
        entity.setReason(request.reason());
        entity.setRequestedByUsername(request.requestedByUsername());
        entity.setReviewedByUsername(request.reviewedByUsername());
        entity.setResolutionNote(request.resolutionNote());
        entity.setCreatedAt(request.createdAt());
        entity.setReviewedAt(request.reviewedAt());
        return entity;
    }
}
