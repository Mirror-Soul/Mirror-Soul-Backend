package com.mirrorsoul.mirrorsoul_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "openai.embedding")
public class OpenAiEmbeddingProperties {

    private boolean enabled;
    private String baseUrl = "https://api.openai.com";
    private String apiKey;
    private String model = "text-embedding-3-small";
    private int dimensions = 1536;
}
