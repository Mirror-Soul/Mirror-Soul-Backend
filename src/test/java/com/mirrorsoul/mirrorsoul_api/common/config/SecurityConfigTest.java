package com.mirrorsoul.mirrorsoul_api.common.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
