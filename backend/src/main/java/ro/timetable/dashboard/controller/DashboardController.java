package ro.timetable.dashboard.controller;

import jakarta.validation.constraints.NotNull;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.timetable.common.dto.ApiDtos.DashboardSummaryResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.dashboard.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AuthenticatedRequestService authenticatedRequestService;
    private final DashboardService dashboardService;

    public DashboardController(
            AuthenticatedRequestService authenticatedRequestService,
            DashboardService dashboardService
    ) {
        this.authenticatedRequestService = authenticatedRequestService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@NotNull JwtAuthenticationToken authentication) {
        return dashboardService.buildSummary(
                authenticatedRequestService.username(authentication),
                authenticatedRequestService.roles(authentication),
                authentication.getToken().getClaims()
        );
    }
}
