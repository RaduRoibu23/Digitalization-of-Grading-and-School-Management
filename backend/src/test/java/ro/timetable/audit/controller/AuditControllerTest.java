package ro.timetable.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.audit.service.AuditService;
import ro.timetable.common.dto.ApiDtos.AuditEntryResponse;
import ro.timetable.common.security.AuthenticatedRequestService;

class AuditControllerTest {

    @Test
    void blocksAuditAccessForNonAdminRoles() {
        AuditService auditService = mock(AuditService.class);
        AuthenticatedRequestService authenticatedRequestService = mock(AuthenticatedRequestService.class);
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        AuditController controller = new AuditController(auditService, authenticatedRequestService);

        when(authenticatedRequestService.hasAnyRole(authentication, "admin", "sysadmin")).thenReturn(false);

        assertThatThrownBy(() -> controller.auditEntries(50, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void returnsAuditEntriesForAdminAndSysadmin() {
        AuditService auditService = mock(AuditService.class);
        AuthenticatedRequestService authenticatedRequestService = mock(AuthenticatedRequestService.class);
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        AuditController controller = new AuditController(auditService, authenticatedRequestService);
        List<AuditEntryResponse> expected = List.of(
                new AuditEntryResponse(1L, "Actualizare profil", "admin01", "Profil actualizat", "2026-04-07T12:00:00Z")
        );

        when(authenticatedRequestService.hasAnyRole(authentication, "admin", "sysadmin")).thenReturn(true);
        when(auditService.latest(25)).thenReturn(expected);

        List<AuditEntryResponse> result = controller.auditEntries(25, authentication);

        assertThat(result).containsExactlyElementsOf(expected);
        verify(auditService).latest(25);
    }
}
