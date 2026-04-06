package ro.timetable.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import ro.timetable.reference.entity.UserProfileEntity;

@Entity
@Table(name = "feedback_entries")
public class FeedbackEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submitted_by_username", length = 100)
    private String submittedByUsername;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_username", referencedColumnName = "username", insertable = false, updatable = false)
    private UserProfileEntity submitter;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 20)
    private String satisfaction;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "wants_contact", nullable = false)
    private boolean wantsContact;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "status_updated_by_username", length = 100)
    private String statusUpdatedByUsername;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @Column(name = "reply_message", columnDefinition = "TEXT")
    private String replyMessage;

    @Column(name = "replied_by_username", length = 100)
    private String repliedByUsername;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubmittedByUsername() {
        return submittedByUsername;
    }

    public void setSubmittedByUsername(String submittedByUsername) {
        this.submittedByUsername = submittedByUsername;
    }

    public UserProfileEntity getSubmitter() {
        return submitter;
    }

    public void setSubmitter(UserProfileEntity submitter) {
        this.submitter = submitter;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSatisfaction() {
        return satisfaction;
    }

    public void setSatisfaction(String satisfaction) {
        this.satisfaction = satisfaction;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isWantsContact() {
        return wantsContact;
    }

    public void setWantsContact(boolean wantsContact) {
        this.wantsContact = wantsContact;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusUpdatedByUsername() {
        return statusUpdatedByUsername;
    }

    public void setStatusUpdatedByUsername(String statusUpdatedByUsername) {
        this.statusUpdatedByUsername = statusUpdatedByUsername;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(Instant statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }

    public String getReplyMessage() {
        return replyMessage;
    }

    public void setReplyMessage(String replyMessage) {
        this.replyMessage = replyMessage;
    }

    public String getRepliedByUsername() {
        return repliedByUsername;
    }

    public void setRepliedByUsername(String repliedByUsername) {
        this.repliedByUsername = repliedByUsername;
    }

    public Instant getRepliedAt() {
        return repliedAt;
    }

    public void setRepliedAt(Instant repliedAt) {
        this.repliedAt = repliedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
