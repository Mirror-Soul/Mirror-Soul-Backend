package com.mirrorsoul.mirrorsoul_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeminiEmbeddingProperties.class)
public class GeminiEmbeddingConfig {

    @Bean(name = "geminiRestClient")
    @ConditionalOnProperty(
            prefix = "gemini.embedding",
            name = "enabled",
            havingValue = "true"
    )
    public RestClient geminiRestClient(
            GeminiEmbeddingProperties properties
    ) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY must be configured when Gemini embeddings are enabled"
            );
        }
        if (properties.getDimensions() != 1536) {
            throw new IllegalStateException(
                    "GEMINI_EMBEDDING_DIMENSIONS must be 1536 for the current vector schema"
            );
        }

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-goog-api-key", properties.getApiKey())
                .build();
    }
}
