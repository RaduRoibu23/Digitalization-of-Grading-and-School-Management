package ro.timetable.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

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
}
