package ro.timetable.reference.model;

import java.util.List;

public record UserProfile(
        Long id,
        Integer version,
        String username,
        String role,
        String firstName,
        String lastName,
        String email,
        String address,
        String cnp,
        String idSeries,
        String serialNumber,
        String fatherInitial,
        Long classId,
        String className,
        List<String> subjectsTaught
) {
}
