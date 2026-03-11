package ro.timetable.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.service.SchoolDataService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReferenceDataController {

    private final SchoolDataService schoolDataService;

    public ReferenceDataController(SchoolDataService schoolDataService) {
        this.schoolDataService = schoolDataService;
    }

    @GetMapping("/classes")
    public List<?> classes() {
        return schoolDataService.getClasses();
    }

    @GetMapping("/subjects")
    public List<?> subjects() {
        return schoolDataService.getSubjects();
    }

    @GetMapping({"/rooms", "/rooms/"})
    public List<?> rooms() {
        return schoolDataService.getRooms();
    }

    @GetMapping("/profiles")
    public List<Map<String, Object>> profiles(@RequestParam(required = false) String role) {
        return schoolDataService.getProfilesByRole(role);
    }
}

