package ro.timetable.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String role;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "cnp", unique = true, length = 13)
    private String cnp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClassEntity schoolClass;

    @ElementCollection
    @CollectionTable(name = "user_profile_subjects", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "subject_name")
    private List<String> subjectsTaught = new ArrayList<>();

    @OneToMany(mappedBy = "recipient")
    private List<NotificationEntity> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "studentProfile")
    private List<StudentGradeEntity> ownedGrades = new ArrayList<>();

    @OneToMany(mappedBy = "teacherProfile")
    private List<StudentGradeEntity> taughtGrades = new ArrayList<>();

    @OneToMany(mappedBy = "teacherProfile")
    private List<TimetableEntryEntity> teachingEntries = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCnp() {
        return cnp;
    }

    public void setCnp(String cnp) {
        this.cnp = cnp;
    }

    public SchoolClassEntity getSchoolClass() {
        return schoolClass;
    }

    public void setSchoolClass(SchoolClassEntity schoolClass) {
        this.schoolClass = schoolClass;
    }

    public List<String> getSubjectsTaught() {
        return subjectsTaught;
    }

    public void setSubjectsTaught(List<String> subjectsTaught) {
        this.subjectsTaught = subjectsTaught;
    }
}
