package ro.timetable.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "school_classes")
public class SchoolClassEntity {

    @Id
    private Long id;

    private String name;

    private String profile;

    @OneToMany(mappedBy = "schoolClass", cascade = CascadeType.ALL)
    private List<UserProfileEntity> students = new ArrayList<>();

    @OneToMany(mappedBy = "schoolClass")
    private List<TimetableEntryEntity> timetableEntries = new ArrayList<>();

    @OneToMany(mappedBy = "schoolClass")
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

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public List<UserProfileEntity> getStudents() {
        return students;
    }

    public void setStudents(List<UserProfileEntity> students) {
        this.students = students;
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
