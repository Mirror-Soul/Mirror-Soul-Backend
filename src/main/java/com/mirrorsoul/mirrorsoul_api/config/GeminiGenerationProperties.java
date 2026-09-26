package com.mirrorsoul.mirrorsoul_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gemini.generation")
public class GeminiGenerationProperties {
    private boolean enabled;
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String apiKey;
    private String model = "gemini-3.6-flash";
    private double temperature = 0.3;
    private int maxOutputTokens = 1500;
    private String promptVersion = "clone-profile-v1";
}
