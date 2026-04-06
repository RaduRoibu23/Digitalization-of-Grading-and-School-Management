package ro.timetable.reference.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import ro.timetable.catalog.entity.StudentGradeEntity;
import ro.timetable.timetable.entity.TimetableEntryEntity;

@Entity
@Table(name = "subjects")
public class SubjectEntity {

    @Id
    private Long id;

    private String name;

    @OneToMany(mappedBy = "subject")
    private List<TimetableEntryEntity> timetableEntries = new ArrayList<>();

    @OneToMany(mappedBy = "subject")
    private List<StudentGradeEntity> grades = new ArrayList<>();

    // #manytomany Profesorii si materiile formeaza acum o relatie many-to-many prin tabela de legatura.
    @ManyToMany(mappedBy = "teachingSubjects")
    private List<UserProfileEntity> teachers = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TimetableEntryEntity> getTimetableEntries() {
        return timetableEntries;
    }

    public void setTimetableEntries(List<TimetableEntryEntity> timetableEntries) {
        this.timetableEntries = timetableEntries;
    }

    public List<StudentGradeEntity> getGrades() {
        return grades;
    }

    public void setGrades(List<StudentGradeEntity> grades) {
        this.grades = grades;
    }

    public List<UserProfileEntity> getTeachers() {
        return teachers;
    }

    public void setTeachers(List<UserProfileEntity> teachers) {
        this.teachers = teachers;
    }
}
