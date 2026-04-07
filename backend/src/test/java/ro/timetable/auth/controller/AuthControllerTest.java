package ro.timetable.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;
import ro.timetable.audit.service.AuditService;
import ro.timetable.auth.service.AccountProvisioningService;
import ro.timetable.common.dto.ApiDtos.ClassSummaryResponse;
import ro.timetable.common.dto.ApiDtos.MeResponse;
import ro.timetable.common.dto.ApiDtos.ProfileSettingsResponse;
import ro.timetable.common.security.AuthenticatedRequestService;
import ro.timetable.reference.service.SchoolDataService;

class AuthControllerTest {

    @Test
    void updateMyProfileSynchronizesIdentityAndRecordsAudit() {
        AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);
        AuditService auditService = mock(AuditService.class);
        AuthenticatedRequestService authenticatedRequestService = mock(AuthenticatedRequestService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        SchoolDataService schoolDataService = mock(SchoolDataService.class);
        AuthController controller = new AuthController(
                accountProvisioningService,
                auditService,
                authenticatedRequestService,
                restTemplate,
                schoolDataService
        );

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("email", "student001@timetable.local")
                .claim("preferred_username", "student001")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        AuthController.UpdateMyProfileRequest request = new AuthController.UpdateMyProfileRequest(
                3,
                "Ana",
                "Popescu",
                "ana.popescu@timetable.local",
                "Str. Republicii nr. 12",
                new AuthController.UpdateMyProfileSettingsRequest(true, false)
        );
        MeResponse updated = new MeResponse(
                1L,
                4,
                "student001",
                "Ana",
                "Popescu",
                "ana.popescu@timetable.local",
                "Str. Republicii nr. 12",
                "6050101030012",
                "AG",
                "123456",
                "P",
                "student",
                List.of("student"),
                1L,
                "IX A",
                "Filologie",
                List.of(),
                Map.of("email", "student001@timetable.local"),
                new ProfileSettingsResponse(true, false),
                new ClassSummaryResponse(1L, "IX A", "Filologie")
        );

        when(authenticatedRequestService.username(authentication)).thenReturn("student001");
        when(authenticatedRequestService.roles(authentication)).thenReturn(List.of("student"));
        when(schoolDataService.updateMyProfile(
                eq("student001"),
                anyList(),
                eq(jwt.getClaims()),
                eq(3),
                eq("Ana"),
                eq("Popescu"),
                eq("ana.popescu@timetable.local"),
                eq("Str. Republicii nr. 12"),
                eq(true),
                eq(false)
        )).thenReturn(updated);

        MeResponse response = controller.updateMyProfile(request, authentication);

        assertThat(response).isEqualTo(updated);
        verify(accountProvisioningService).syncManagedAccountProfile(
                "student001",
                "Ana",
                "Popescu",
                "ana.popescu@timetable.local"
        );
        verify(auditService).record(
                "Actualizare profil propriu",
                "student001",
                "Utilizatorul si-a actualizat datele personale si preferintele de notificare"
        );
    }
}
