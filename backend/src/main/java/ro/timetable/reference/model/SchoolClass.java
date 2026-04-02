package ro.timetable.reference.model;

public record SchoolClass(
        Long id,
        String name,
        String profile,
        String homeroomTeacherUsername,
        String homeroomTeacherName
) {
}
