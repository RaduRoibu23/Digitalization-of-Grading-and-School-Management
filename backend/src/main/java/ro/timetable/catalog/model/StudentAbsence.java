package ro.timetable.catalog.model;

public record StudentAbsence(
        Long id,
        String studentUsername,
        String studentName,
        Long classId,
        String className,
        Long subjectId,
        String subjectName,
        String absenceDate,
        String teacherUsername,
        String teacherName,
        boolean motivated,
        String motivatedByUsername,
        String motivatedByName,
        String motivatedAt,
        Integer version
) {
}
