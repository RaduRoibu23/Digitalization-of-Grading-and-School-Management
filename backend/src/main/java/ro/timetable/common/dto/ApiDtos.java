package ro.timetable.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import ro.timetable.common.dto.ApiDtos.AbsenceResponse;
import ro.timetable.common.dto.ApiDtos.ActionResponse;
import ro.timetable.common.dto.ApiDtos.ApiErrorResponse;
import ro.timetable.common.dto.ApiDtos.AuditEntryResponse;
import ro.timetable.common.dto.ApiDtos.CatalogResponse;
import ro.timetable.common.dto.ApiDtos.CatalogSubjectResponse;
import ro.timetable.common.dto.ApiDtos.ClassSummaryResponse;
import ro.timetable.common.dto.ApiDtos.DashboardMetricResponse;
import ro.timetable.common.dto.ApiDtos.DashboardQuickActionResponse;
import ro.timetable.common.dto.ApiDtos.DashboardSummaryResponse;
import ro.timetable.common.dto.ApiDtos.DashboardTimetableEntryResponse;
import ro.timetable.common.dto.ApiDtos.AnnouncementResponse;
import ro.timetable.common.dto.ApiDtos.DocumentRequestResponse;
import ro.timetable.common.dto.ApiDtos.FeedbackEntryResponse;
import ro.timetable.common.dto.ApiDtos.GradeResponse;
import ro.timetable.common.dto.ApiDtos.HealthResponse;
import ro.timetable.common.dto.ApiDtos.LoginResponse;
import ro.timetable.common.dto.ApiDtos.MeResponse;
import ro.timetable.common.dto.ApiDtos.MailStatusResponse;
import ro.timetable.common.dto.ApiDtos.NotificationDispatchResponse;
import ro.timetable.common.dto.ApiDtos.NotificationResponse;
import ro.timetable.common.dto.ApiDtos.ProfileResponse;
import ro.timetable.common.dto.ApiDtos.RegistrationResponse;
import ro.timetable.common.dto.ApiDtos.TimetableGenerationResponse;
import ro.timetable.common.dto.ApiDtos.TimetableMoveOptionsResponse;
import ro.timetable.common.dto.ApiDtos.TimetableMoveResponse;
import ro.timetable.common.dto.ApiDtos.TimetableSlotMoveOptionResponse;
import ro.timetable.common.dto.ApiDtos.UnreadNotificationCountResponse;
import ro.timetable.timetable.model.TimetableEntry;

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
            String homeroom_class_name,
            String linked_student_username,
            String linked_student_name,
            Long linked_student_class_id,
            String linked_student_class_name,
            boolean is_external
    ) {
    }

    public record ProfileSettingsResponse(
            boolean email_notifications_enabled,
            boolean in_app_notifications_enabled
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
            ProfileSettingsResponse settings,
            @JsonProperty("class") ClassSummaryResponse school_class,
            String linked_student_username,
            String linked_student_name,
            Long linked_student_class_id,
            String linked_student_class_name
    ) {
    }

    public record TimetableGenerationResponse(
            String detail,
            List<Long> job_ids,
            boolean partial,
            List<TimetableUnassignedItemResponse> unassigned_items
    ) {
    }

    public record TimetableUnassignedItemResponse(
            Long class_id,
            String class_name,
            Long subject_id,
            String subject_name,
            Integer missing_hours,
            List<String> reason_codes
    ) {
    }

    public record TimetableSlotMoveOptionResponse(
            Integer weekday,
            Integer index_in_day,
            String status,
            String mode,
            Long target_entry_id,
            String target_subject_name,
            String target_teacher_name,
            String target_room_name,
            List<String> warnings,
            String blocked_reason
    ) {
    }

    public record TimetableMoveOptionsResponse(
            Long source_entry_id,
            Integer source_version,
            Integer source_weekday,
            Integer source_index_in_day,
            List<TimetableSlotMoveOptionResponse> slot_options
    ) {
    }

    public record TimetableMoveResponse(
            String detail,
            String mode,
            List<String> warnings,
            List<TimetableEntry> affected_entries
    ) {
    }

    public record NotificationResponse(
            Long id,
            String recipient_username,
            String title,
            String category,
            String action_path,
            String message,
            boolean read,
            String read_at,
            String created_at
    ) {
    }

    public record UnreadNotificationCountResponse(long unread_count) {
    }

    public record DashboardMetricResponse(
            String id,
            String label,
            String value,
            String detail,
            String tone
    ) {
    }

    public record DashboardQuickActionResponse(
            String label,
            String path,
            String description,
            String tone
    ) {
    }

    public record DashboardTimetableEntryResponse(
            Long id,
            String subject_name,
            String class_name,
            String room_name,
            String teacher_name,
            Integer weekday,
            Integer index_in_day,
            String time_label
    ) {
    }

    public record AnnouncementResponse(
            Long id,
            String title,
            String message,
            String created_by_username,
            String created_at
    ) {
    }

    public record DashboardSummaryResponse(
            String role_context,
            String title,
            String subtitle,
            List<DashboardMetricResponse> metrics,
            List<DashboardQuickActionResponse> quick_actions,
            List<AnnouncementResponse> announcements,
            boolean can_publish_announcements,
            List<DashboardTimetableEntryResponse> today_timetable,
            DashboardTimetableEntryResponse next_entry,
            List<NotificationResponse> recent_notifications,
            List<DocumentRequestResponse> pending_documents,
            List<FeedbackEntryResponse> recent_feedback
    ) {
    }

    public record MailStatusResponse(
            boolean enabled,
            boolean configured,
            String smtp_host,
            Integer smtp_port,
            String delivery_mode,
            String from_address,
            String detail
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
            String comment,
            Integer version,
            boolean editable,
            boolean can_request_change,
            Long pending_request_id,
            String pending_request_status,
            String pending_request_type
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
            String motivation_reason,
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

    public record GradeChangeRequestResponse(
            Long id,
            Long grade_id,
            String request_type,
            String status,
            Integer base_grade_version,
            Integer current_grade_value,
            String current_grade_date,
            String current_comment,
            Integer proposed_grade_value,
            String proposed_grade_date,
            String proposed_comment,
            String reason,
            String requested_by_username,
            String reviewed_by_username,
            String resolution_note,
            String created_at,
            String reviewed_at,
            boolean can_review
    ) {
    }

    public record CurriculumProfileSummaryResponse(
            String profile_name,
            List<String> levels,
            Map<String, Integer> total_weekly_hours_by_level,
            List<String> representative_subjects,
            String summary,
            List<String> details,
            String accent
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
