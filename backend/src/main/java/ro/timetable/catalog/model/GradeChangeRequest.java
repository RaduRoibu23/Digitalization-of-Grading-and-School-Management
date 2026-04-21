package ro.timetable.catalog.model;

import java.time.Instant;

public record GradeChangeRequest(
        Long id,
        Long gradeId,
        String requestType,
        String status,
        Integer baseGradeVersion,
        Integer proposedGradeValue,
        String proposedGradeDate,
        String proposedComment,
        String reason,
        String requestedByUsername,
        String reviewedByUsername,
        String resolutionNote,
        Instant createdAt,
        Instant reviewedAt
) {
}
