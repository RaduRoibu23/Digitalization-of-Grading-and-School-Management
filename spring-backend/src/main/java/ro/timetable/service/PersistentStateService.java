package ro.timetable.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.timetable.model.StudentGrade;
import ro.timetable.model.TimetableEntry;
import ro.timetable.persistence.StudentGradeEntity;
import ro.timetable.persistence.StudentGradeRepository;
import ro.timetable.persistence.TimetableEntryEntity;
import ro.timetable.persistence.TimetableEntryRepository;

import java.util.List;

@Service
public class PersistentStateService {

    private final TimetableEntryRepository timetableEntryRepository;
    private final StudentGradeRepository studentGradeRepository;

    public PersistentStateService(TimetableEntryRepository timetableEntryRepository, StudentGradeRepository studentGradeRepository) {
        this.timetableEntryRepository = timetableEntryRepository;
        this.studentGradeRepository = studentGradeRepository;
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
        entity.setVersion(grade.version());
        return entity;
    }
}
