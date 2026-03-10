package ro.timetable.model;

import java.util.List;

public record UserProfile(
        Long id,
        String username,
        String role,
        String firstName,
        String lastName,
        String email,
        Long classId,
        String className,
        List<String> subjectsTaught
) {
}
