package com.mirrorsoul.mirrorsoul_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.ai")
public record AiInternalApiProperties(String apiKey) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
