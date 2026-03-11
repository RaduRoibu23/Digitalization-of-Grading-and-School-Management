package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.model.TimetableEntry;
import ro.timetable.model.TimetableGenerationRequest;
import ro.timetable.service.SchoolDataService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetables")
public class TimetableController {

    private final SchoolDataService schoolDataService;

    public TimetableController(SchoolDataService schoolDataService) {
        this.schoolDataService = schoolDataService;
    }

    public record UpdateTimetableEntryRequest(
            @NotNull Integer version,
            Long subject_id,
            Long room_id
    ) {
    }

    @GetMapping("/classes/{classId}")
    public List<TimetableEntry> timetableForClass(@PathVariable Long classId) {
        return schoolDataService.getTimetableForClass(classId);
    }

    @GetMapping("/me/teacher")
    public List<TimetableEntry> timetableForTeacher(org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken authentication) {
        return schoolDataService.getTimetableForTeacher(authentication.getName());
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(@Valid @RequestBody TimetableGenerationRequest request) {
        if (request.class_id() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "class_id is required");
        }
        return schoolDataService.generateTimetable(request.class_id());
    }

    @DeleteMapping("/classes/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long classId) {
        schoolDataService.deleteTimetable(classId);
    }

    @PatchMapping("/entries/{entryId}")
    public TimetableEntry updateEntry(@PathVariable Long entryId, @Valid @RequestBody UpdateTimetableEntryRequest request) {
        return schoolDataService.updateEntry(entryId, request.version(), request.subject_id(), request.room_id());
    }
}

