package ro.timetable.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.service.RegistrationService;
import ro.timetable.service.SchoolDataService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final SchoolDataService schoolDataService;
    private final RegistrationService registrationService;

    public RegistrationController(SchoolDataService schoolDataService, RegistrationService registrationService) {
        this.schoolDataService = schoolDataService;
        this.registrationService = registrationService;
    }

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String first_name,
            @NotBlank String last_name,
            @NotBlank @Email String email,
            @NotNull Long class_id
    ) {
    }

    @GetMapping("/public/classes")
    public List<?> publicClasses() {
        return schoolDataService.getPublicClasses();
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.registerStudent(
                request.username().trim(),
                request.password(),
                request.first_name().trim(),
                request.last_name().trim(),
                request.email().trim(),
                request.class_id()
        );
    }
}
