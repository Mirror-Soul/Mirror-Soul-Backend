package com.mirrorsoul.mirrorsoul_api.common.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.mirrorsoul.mirrorsoul_api.common.jwt.JwtAuthenticationFilter;
import com.mirrorsoul.mirrorsoul_api.common.security.AiInternalApiKeyFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;

@SpringJUnitConfig
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigTest.TestConfiguration.class)
class SecurityConfigTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void internalApiKeyFilterRunsBeforeJwtFilter() {
        List<?> filters = securityFilterChain.getFilters();
        int internalApiKeyIndex = indexOf(filters, AiInternalApiKeyFilter.class);
        int jwtIndex = indexOf(filters, JwtAuthenticationFilter.class);

        assertTrue(internalApiKeyIndex >= 0);
        assertTrue(jwtIndex >= 0);
        assertTrue(internalApiKeyIndex < jwtIndex);
    }

    @Test
    void adminPathRejectsOrdinaryUsersAndAcceptsAdmins() throws Exception {
        AuthorizationFilter authorization = (AuthorizationFilter) securityFilterChain.getFilters()
                .stream().filter(AuthorizationFilter.class::isInstance).findFirst().orElseThrow();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/job-verifications");
        request.setServletPath("/admin/job-verifications");

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("user", null, Collections.emptyList()));
            assertThatThrownBy(() -> authorization.doFilter(
                    request, new MockHttpServletResponse(), new MockFilterChain()))
                    .isInstanceOf(AccessDeniedException.class);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            authorization.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private int indexOf(List<?> filters, Class<?> filterClass) {
        for (int index = 0; index < filters.size(); index++) {
            if (filterClass.isInstance(filters.get(index))) {
                return index;
            }
        }
        return -1;
    }

    @Configuration
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfiguration {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return mock(JwtAuthenticationFilter.class);
        }

        @Bean
        AiInternalApiKeyFilter aiInternalApiKeyFilter() {
            return mock(AiInternalApiKeyFilter.class);
        }
    }
}
