package com.mirrorsoul.mirrorsoul_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAiEmbeddingProperties.class)
public class OpenAiEmbeddingConfig {

    @Bean(name = "openAiRestClient")
    @ConditionalOnProperty(
            prefix = "openai.embedding",
            name = "enabled",
            havingValue = "true"
    )
    public RestClient openAiRestClient(
            RestClient.Builder builder,
            OpenAiEmbeddingProperties properties
    ) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY must be configured when OpenAI embeddings are enabled"
            );
        }
        if (properties.getDimensions() != 1536) {
            throw new IllegalStateException(
                    "OPENAI_EMBEDDING_DIMENSIONS must be 1536 for the current vector schema"
            );
        }

        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.getApiKey()
                )
                .build();
    }
}
