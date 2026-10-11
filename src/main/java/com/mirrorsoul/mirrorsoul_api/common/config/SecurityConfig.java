package com.mirrorsoul.mirrorsoul_api.common.config;

import com.mirrorsoul.mirrorsoul_api.common.jwt.JwtAuthenticationFilter;
import com.mirrorsoul.mirrorsoul_api.common.jwt.JwtProperties;
import com.mirrorsoul.mirrorsoul_api.common.security.AiInternalApiKeyFilter;
import com.mirrorsoul.mirrorsoul_api.config.AiInternalApiProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({JwtProperties.class, AiInternalApiProperties.class})
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${security.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.mirrorsoul.mirrorsoul_api.access.AdminAuditWriter adminAuditWriter;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AiInternalApiKeyFilter aiInternalApiKeyFilter;

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (adminAuditWriter != null) http.addFilterAfter(new com.mirrorsoul.mirrorsoul_api.access.AdminSecurityAuditFilter(adminAuditWriter), JwtAuthenticationFilter.class);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                "/regions/**",
                                "/onboarding/regions/**"
                        ).permitAll()
                        .requestMatchers(
                                "/join/**",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/password-reset/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/ws/signaling"
                                , "/internal/value-balance/analysis-jobs/**"
                                , "/internal/clone-training/*/personality/complete"
                                , "/internal/clone-training/*/voice/complete"
                        ).permitAll()
                        .requestMatchers("/internal/ai/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin/me", "/admin/staff", "/admin/audit-events", "/admin/members", "/admin/members/**")
                            .hasAnyRole("ADMIN", "ADMIN_VIEWER", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/admin/reports", "/admin/reports/*", "/admin/reports/*/decisions")
                            .hasAnyRole("ADMIN", "ADMIN_VIEWER", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/admin/support-tickets", "/admin/support-tickets/*")
                            .hasAnyRole("ADMIN", "ADMIN_VIEWER", "SUPER_ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(aiInternalApiKeyFilter, JwtAuthenticationFilter.class)
                .cors(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
