package ro.timetable.model;

public record TimetableEntry(
        Long id,
        Long classId,
        String className,
        Long subjectId,
        String subjectName,
        Long roomId,
        String roomName,
        String teacherUsername,
        String teacherName,
        Integer weekday,
        Integer indexInDay,
        Integer version
) {
    public TimetableEntry withSubject(Long newSubjectId, String newSubjectName) {
        return new TimetableEntry(id, classId, className, newSubjectId, newSubjectName, roomId, roomName, teacherUsername, teacherName, weekday, indexInDay, version + 1);
    }

    public TimetableEntry withRoom(Long newRoomId, String newRoomName) {
        return new TimetableEntry(id, classId, className, subjectId, subjectName, newRoomId, newRoomName, teacherUsername, teacherName, weekday, indexInDay, version + 1);
    }

    public TimetableEntry withSubjectAndRoom(Long newSubjectId, String newSubjectName, Long newRoomId, String newRoomName) {
        return new TimetableEntry(id, classId, className, newSubjectId, newSubjectName, newRoomId, newRoomName, teacherUsername, teacherName, weekday, indexInDay, version + 1);
    }
}
