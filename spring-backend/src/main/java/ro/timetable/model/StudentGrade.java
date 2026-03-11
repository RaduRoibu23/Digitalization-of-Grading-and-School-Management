package ro.timetable.model;

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
        Integer version
) {
}
