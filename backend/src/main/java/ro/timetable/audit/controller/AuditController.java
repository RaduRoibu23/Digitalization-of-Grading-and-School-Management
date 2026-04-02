package ro.timetable.audit.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.AuditEntryResponse;

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
