package ro.timetable.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "grade_change_requests")
public class GradeChangeRequestEntity {

    @Id
    private Long id;

    @Column(name = "grade_id", nullable = false)
    private Long gradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", insertable = false, updatable = false)
    private StudentGradeEntity grade;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "base_grade_version", nullable = false)
    private Integer baseGradeVersion;

    @Column(name = "proposed_grade_value")
    private Integer proposedGradeValue;

    @Column(name = "proposed_grade_date")
    private String proposedGradeDate;

    @Column(name = "proposed_comment", columnDefinition = "TEXT")
    private String proposedComment;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "requested_by_username", nullable = false)
    private String requestedByUsername;

    @Column(name = "reviewed_by_username")
    private String reviewedByUsername;

    @Column(name = "resolution_note", length = 255)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGradeId() {
        return gradeId;
    }

    public void setGradeId(Long gradeId) {
        this.gradeId = gradeId;
    }

    public StudentGradeEntity getGrade() {
        return grade;
    }

    public void setGrade(StudentGradeEntity grade) {
        this.grade = grade;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getBaseGradeVersion() {
        return baseGradeVersion;
    }

    public void setBaseGradeVersion(Integer baseGradeVersion) {
        this.baseGradeVersion = baseGradeVersion;
    }

    public Integer getProposedGradeValue() {
        return proposedGradeValue;
    }

    public void setProposedGradeValue(Integer proposedGradeValue) {
        this.proposedGradeValue = proposedGradeValue;
    }

    public String getProposedGradeDate() {
        return proposedGradeDate;
    }

    public void setProposedGradeDate(String proposedGradeDate) {
        this.proposedGradeDate = proposedGradeDate;
    }

    public String getProposedComment() {
        return proposedComment;
    }

    public void setProposedComment(String proposedComment) {
        this.proposedComment = proposedComment;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestedByUsername() {
        return requestedByUsername;
    }

    public void setRequestedByUsername(String requestedByUsername) {
        this.requestedByUsername = requestedByUsername;
    }

    public String getReviewedByUsername() {
        return reviewedByUsername;
    }

    public void setReviewedByUsername(String reviewedByUsername) {
        this.reviewedByUsername = reviewedByUsername;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
