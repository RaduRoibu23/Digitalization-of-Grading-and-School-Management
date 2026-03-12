package ro.timetable.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_grades")
public class StudentGradeEntity {

    @Id
    private Long id;

    @Column(name = "student_username", nullable = false)
    private String studentUsername;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_username", referencedColumnName = "username", insertable = false, updatable = false)
    private UserProfileEntity studentProfile;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", insertable = false, updatable = false)
    private SchoolClassEntity schoolClass;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", insertable = false, updatable = false)
    private SubjectEntity subject;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "grade_value", nullable = false)
    private Integer gradeValue;

    @Column(name = "grade_date", nullable = false)
    private String gradeDate;

    @Column(name = "teacher_username", nullable = false)
    private String teacherUsername;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_username", referencedColumnName = "username", insertable = false, updatable = false)
    private UserProfileEntity teacherProfile;

    @Column(name = "teacher_name", nullable = false)
    private String teacherName;

    @Column(nullable = false)
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public void setStudentUsername(String studentUsername) {
        this.studentUsername = studentUsername;
    }

    public UserProfileEntity getStudentProfile() {
        return studentProfile;
    }

    public void setStudentProfile(UserProfileEntity studentProfile) {
        this.studentProfile = studentProfile;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public SchoolClassEntity getSchoolClass() {
        return schoolClass;
    }

    public void setSchoolClass(SchoolClassEntity schoolClass) {
        this.schoolClass = schoolClass;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public SubjectEntity getSubject() {
        return subject;
    }

    public void setSubject(SubjectEntity subject) {
        this.subject = subject;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public Integer getGradeValue() {
        return gradeValue;
    }

    public void setGradeValue(Integer gradeValue) {
        this.gradeValue = gradeValue;
    }

    public String getGradeDate() {
        return gradeDate;
    }

    public void setGradeDate(String gradeDate) {
        this.gradeDate = gradeDate;
    }

    public String getTeacherUsername() {
        return teacherUsername;
    }

    public void setTeacherUsername(String teacherUsername) {
        this.teacherUsername = teacherUsername;
    }

    public UserProfileEntity getTeacherProfile() {
        return teacherProfile;
    }

    public void setTeacherProfile(UserProfileEntity teacherProfile) {
        this.teacherProfile = teacherProfile;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
