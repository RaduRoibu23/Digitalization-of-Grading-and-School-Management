package ro.timetable.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.allowed-origin-patterns:http://localhost:*,https://localhost:*,http://127.0.0.1:*,https://127.0.0.1:*,http://192.168.*:*,https://192.168.*:*,http://10.*:*," +
            "https://10.*:*,http://172.16.*:*,https://172.16.*:*,http://172.17.*:*,https://172.17.*:*,http://172.18.*:*,https://172.18.*:*," +
            "http://172.19.*:*,https://172.19.*:*,http://172.20.*:*,https://172.20.*:*,http://172.21.*:*,https://172.21.*:*,http://172.22.*:*," +
            "https://172.22.*:*,http://172.23.*:*,https://172.23.*:*,http://172.24.*:*,https://172.24.*:*,http://172.25.*:*,https://172.25.*:*," +
            "http://172.26.*:*,https://172.26.*:*,http://172.27.*:*,https://172.27.*:*,http://172.28.*:*,https://172.28.*:*,http://172.29.*:*," +
            "https://172.29.*:*,http://172.30.*:*,https://172.30.*:*,http://172.31.*:*,https://172.31.*:*}")
    private List<String> allowedOriginPatterns;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/api/health",
                                "/api/login",
                                "/api/refresh",
                                "/api/public/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Content-Disposition", "X-Download-Filename"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
