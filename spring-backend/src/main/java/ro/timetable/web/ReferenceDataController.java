package ro.timetable.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.model.Room;
import ro.timetable.model.SchoolClass;
import ro.timetable.model.Subject;
import ro.timetable.service.SchoolDataService;
import ro.timetable.web.dto.ApiDtos.ProfileResponse;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReferenceDataController {

    private final SchoolDataService schoolDataService;

    public ReferenceDataController(SchoolDataService schoolDataService) {
        this.schoolDataService = schoolDataService;
    }

    @GetMapping("/classes")
    public List<SchoolClass> classes() {
        return schoolDataService.getClasses();
    }

    @GetMapping("/subjects")
    public List<Subject> subjects() {
        return schoolDataService.getSubjects();
    }

    @GetMapping({"/rooms", "/rooms/"})
    public List<Room> rooms() {
        return schoolDataService.getRooms();
    }

    @GetMapping("/profiles")
    public List<ProfileResponse> profiles(@RequestParam(required = false) String role) {
        return schoolDataService.getProfilesByRole(role);
    }
}