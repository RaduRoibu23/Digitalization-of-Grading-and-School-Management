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
            String username,
            String role,
            String first_name,
            String last_name,
            String email,
            Long class_id,
            String class_name,
            String class_profile,
            List<String> subjects_taught
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
            String username,
            String first_name,
            String last_name,
            String email,
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationDispatchResponse(
            String detail,
            Integer recipients,
            NotificationResponse notification
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

    public record CatalogSubjectResponse(
            Long subject_id,
            String subject_name,
            Integer weekly_hours,
            Integer minimum_grades_for_average,
            Double average,
            List<String> teacher_names,
            List<GradeResponse> grades,
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
            String username,
            String role,
            String first_name,
            String last_name,
            String email,
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