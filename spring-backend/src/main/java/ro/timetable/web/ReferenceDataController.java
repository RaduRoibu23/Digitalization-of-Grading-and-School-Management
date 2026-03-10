package ro.timetable.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.service.DemoSchoolService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReferenceDataController {

    private final DemoSchoolService demoSchoolService;

    public ReferenceDataController(DemoSchoolService demoSchoolService) {
        this.demoSchoolService = demoSchoolService;
    }

    @GetMapping("/classes")
    public List<?> classes() {
        return demoSchoolService.getClasses();
    }

    @GetMapping("/subjects")
    public List<?> subjects() {
        return demoSchoolService.getSubjects();
    }

    @GetMapping({"/rooms", "/rooms/"})
    public List<?> rooms() {
        return demoSchoolService.getRooms();
    }

    @GetMapping("/profiles")
    public List<Map<String, Object>> profiles(@RequestParam(required = false) String role) {
        return demoSchoolService.getProfilesByRole(role);
    }
}
