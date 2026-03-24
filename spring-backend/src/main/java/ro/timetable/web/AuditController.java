package ro.timetable.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.service.AuditService;
import ro.timetable.web.dto.ApiDtos.AuditEntryResponse;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> auditEntries(@RequestParam(required = false) Integer limit) {
        return auditService.latest(limit);
    }
}
