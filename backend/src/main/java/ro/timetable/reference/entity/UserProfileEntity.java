package ro.timetable.reference.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import ro.timetable.timetable.entity.TimetableEntryEntity;
import ro.timetable.catalog.entity.StudentGradeEntity;
import ro.timetable.notifications.entity.NotificationEntity;

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

    @Column(name = "id_series", length = 2)
    private String idSeries;

    @Column(name = "serial_number", length = 6)
    private String serialNumber;

    @Column(name = "father_initial", length = 1)
    private String fatherInitial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClassEntity schoolClass;

    @ManyToMany
    @JoinTable(
            name = "user_profile_subject_links",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    private List<SubjectEntity> teachingSubjects = new ArrayList<>();

    @OneToOne(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserProfileSettingsEntity settings;

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

    public String getIdSeries() {
        return idSeries;
    }

    public void setIdSeries(String idSeries) {
        this.idSeries = idSeries;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getFatherInitial() {
        return fatherInitial;
    }

    public void setFatherInitial(String fatherInitial) {
        this.fatherInitial = fatherInitial;
    }

    public SchoolClassEntity getSchoolClass() {
        return schoolClass;
    }

    public void setSchoolClass(SchoolClassEntity schoolClass) {
        this.schoolClass = schoolClass;
    }

    public List<SubjectEntity> getTeachingSubjects() {
        return teachingSubjects;
    }

    public void setTeachingSubjects(List<SubjectEntity> teachingSubjects) {
        this.teachingSubjects = teachingSubjects == null ? new ArrayList<>() : new ArrayList<>(teachingSubjects);
    }

    public UserProfileSettingsEntity getSettings() {
        return settings;
    }

    public void setSettings(UserProfileSettingsEntity settings) {
        this.settings = settings;
        if (settings != null) {
            settings.setProfile(this);
        }
    }
}
