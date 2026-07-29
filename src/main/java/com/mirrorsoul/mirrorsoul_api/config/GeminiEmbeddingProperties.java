package com.mirrorsoul.mirrorsoul_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gemini.embedding")
public class GeminiEmbeddingProperties {

    private boolean enabled;
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String apiKey;
    private String model = "gemini-embedding-001";
    private int dimensions = 1536;
    private String taskType = "SEMANTIC_SIMILARITY";
}
