package ro.timetable.reference.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.common.dto.ApiDtos.CurriculumProfileSummaryResponse;
import ro.timetable.reference.service.CurriculumPlanService;

@RestController
@RequestMapping("/api/public")
public class PublicReferenceController {

    private final CurriculumPlanService curriculumPlanService;

    public PublicReferenceController(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @GetMapping("/curriculum-profiles")
    public List<CurriculumProfileSummaryResponse> curriculumProfiles() {
        return curriculumPlanService.profileSummaries();
    }
}
