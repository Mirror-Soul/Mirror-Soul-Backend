package com.mirrorsoul.mirrorsoul_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({GeminiGenerationProperties.class, CloneProfileGenerationProperties.class})
public class GeminiGenerationConfig {

    @Bean(name = "geminiGenerationRestClient")
    @ConditionalOnProperty(prefix = "gemini.generation", name = "enabled", havingValue = "true")
    public RestClient geminiGenerationRestClient(GeminiGenerationProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("GEMINI_API_KEY must be configured when Gemini generation is enabled");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("GEMINI_GENERATION_MODEL must be configured when Gemini generation is enabled");
        }
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-goog-api-key", properties.getApiKey())
                .build();
    }
}
