package ro.timetable.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class ApiDtos {

    private ApiDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiErrorResponse(
            String error,
            String detail,
            Integer status,
            String message,
            Map<String, String> fieldErrors
    ) {
    }

    public record HealthResponse(String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActionResponse(
            String detail,
            Long id,
            Integer recipients
    ) {
    }

    public record ProfileResponse(
            Long id,
            Integer version,
            String username,
            String role,
            String first_name,
            String last_name,
            String email,
            String address,
            String cnp,
            String series,
            String serial_number,
            String father_initial,
            Long class_id,
            String class_name,
            String class_profile,
            List<String> subjects_taught,
            Long homeroom_class_id,
            String homeroom_class_name
    ) {
    }

    public record ClassSummaryResponse(
            Long id,
            String name,
            String profile
    ) {
    }

    public record MeResponse(
            Long id,
            Integer version,
            String username,
            String first_name,
            String last_name,
            String email,
            String address,
            String cnp,
            String series,
            String serial_number,
            String father_initial,
            String role,
            List<String> roles,
            Long class_id,
            String class_name,
            String class_profile,
            List<String> subjects_taught,
            Map<String, Object> claims,
            @JsonProperty("class") ClassSummaryResponse school_class
    ) {
    }

    public record TimetableGenerationResponse(
            String detail,
            List<Long> job_ids
    ) {
    }

    public record NotificationResponse(
            Long id,
            String recipient_username,
            String message,
            boolean read,
            String created_at
    ) {
    }

    public record AuditEntryResponse(
            Long id,
            String action,
            String actor_username,
            String effect,
            String created_at
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationDispatchResponse(
            String detail,
            Integer recipients,
            NotificationResponse notification
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DocumentRequestResponse(
            Long id,
            String type,
            String type_label,
            String status,
            String purpose,
            String series,
            Integer document_number,
            String student_username,
            String requested_by_username,
            String reviewed_by_username,
            String resolution_note,
            String created_at,
            String reviewed_at,
            boolean can_approve,
            boolean can_download
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeedbackEntryResponse(
            Long id,
            String category,
            String category_label,
            String satisfaction,
            String satisfaction_label,
            boolean wants_contact,
            String status,
            String status_label,
            String message,
            String reply_message,
            String submitted_by_username,
            String replied_by_username,
            String status_updated_by_username,
            String submitted_at,
            String replied_at,
            String status_updated_at,
            boolean can_reply,
            boolean can_update_status
    ) {
    }

    public record GradeResponse(
            Long id,
            String student_username,
            String student_name,
            Long class_id,
            String class_name,
            Long subject_id,
            String subject_name,
            Integer grade_value,
            String grade_date,
            String teacher_username,
            String teacher_name,
            Integer version,
            boolean editable
    ) {
    }

    public record AbsenceResponse(
            Long id,
            String student_username,
            String student_name,
            Long class_id,
            String class_name,
            Long subject_id,
            String subject_name,
            String absence_date,
            String teacher_username,
            String teacher_name,
            boolean motivated,
            String motivated_by_username,
            String motivated_by_name,
            String motivated_at,
            Integer version,
            boolean motivatable
    ) {
    }

    public record CatalogSubjectResponse(
            Long subject_id,
            String subject_name,
            Integer weekly_hours,
            Integer minimum_grades_for_average,
            Double average,
            List<String> teacher_names,
            List<GradeResponse> grades,
            List<AbsenceResponse> absences,
            boolean can_add
    ) {
    }

    public record CatalogResponse(
            ProfileResponse student,
            List<CatalogSubjectResponse> subjects,
            boolean can_edit
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegistrationResponse(
            String detail,
            Long id,
            Integer version,
            String username,
            String role,
            String first_name,
            String last_name,
            String email,
            String address,
            String cnp,
            String series,
            String serial_number,
            String father_initial,
            Long class_id,
            String class_name,
            String class_profile,
            List<String> subjects_taught
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(
            @JsonProperty("access_token") String access_token,
            @JsonProperty("expires_in") Integer expires_in,
            @JsonProperty("refresh_expires_in") Integer refresh_expires_in,
            @JsonProperty("refresh_token") String refresh_token,
            @JsonProperty("token_type") String token_type,
            @JsonProperty("id_token") String id_token,
            @JsonProperty("not-before-policy") Integer not_before_policy,
            @JsonProperty("session_state") String session_state,
            @JsonProperty("scope") String scope
    ) {
    }
}
