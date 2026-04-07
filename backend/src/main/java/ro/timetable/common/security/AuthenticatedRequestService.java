package ro.timetable.common.security;

import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import ro.timetable.reference.service.SchoolDataService;

@Service
public class AuthenticatedRequestService {

    private static final List<String> APP_ROLES = List.of("student", "professor", "secretariat", "scheduler", "admin", "sysadmin");

    private final SchoolDataService schoolDataService;

    public AuthenticatedRequestService(SchoolDataService schoolDataService) {
        this.schoolDataService = schoolDataService;
    }

    public String username(JwtAuthenticationToken authentication) {
        return schoolDataService.resolveAuthenticatedUsername(preferredUsername(authentication), email(authentication));
    }

    public String preferredUsername(JwtAuthenticationToken authentication) {
        return (String) authentication.getToken().getClaims().getOrDefault("preferred_username", authentication.getName());
    }

    public String email(JwtAuthenticationToken authentication) {
        Object emailClaim = authentication.getToken().getClaims().get("email");
        return emailClaim == null ? null : String.valueOf(emailClaim);
    }

    public List<String> roles(JwtAuthenticationToken authentication) {
        Object realmAccess = authentication.getToken().getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            Object roleValues = realmAccessMap.get("roles");
            if (roleValues instanceof List<?> roleList) {
                return roleList.stream()
                        .map(String::valueOf)
                        .filter(APP_ROLES::contains)
                        .toList();
            }
        }
        return List.of();
    }

    public boolean hasAnyRole(JwtAuthenticationToken authentication, String... expectedRoles) {
        List<String> currentRoles = roles(authentication);
        for (String expectedRole : expectedRoles) {
            if (currentRoles.contains(expectedRole)) {
                return true;
            }
        }
        return false;
    }
}
