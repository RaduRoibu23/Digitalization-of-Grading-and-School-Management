package ro.timetable.audit.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.AuditEntryResponse;
import ro.timetable.common.security.AuthenticatedRequestService;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditService auditService;
    private final AuthenticatedRequestService authenticatedRequestService;

    public AuditController(AuditService auditService, AuthenticatedRequestService authenticatedRequestService) {
        this.auditService = auditService;
        this.authenticatedRequestService = authenticatedRequestService;
    }

    @GetMapping
    public List<AuditEntryResponse> auditEntries(
            @RequestParam(required = false) Integer limit,
            JwtAuthenticationToken authentication
    ) {
        if (!(authenticatedRequestService.hasAnyRole(authentication, "admin", "sysadmin"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doar adminul si sysadminul pot accesa auditul.");
        }
        return auditService.latest(limit);
    }
}
