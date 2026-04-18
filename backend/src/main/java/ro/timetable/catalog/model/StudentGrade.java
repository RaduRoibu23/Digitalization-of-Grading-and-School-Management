package ro.timetable.catalog.model;

public record StudentGrade(
        Long id,
        String studentUsername,
        String studentName,
        Long classId,
        String className,
        Long subjectId,
        String subjectName,
        Integer gradeValue,
        String gradeDate,
        String teacherUsername,
        String teacherName,
        String comment,
        Integer version
) {
}
