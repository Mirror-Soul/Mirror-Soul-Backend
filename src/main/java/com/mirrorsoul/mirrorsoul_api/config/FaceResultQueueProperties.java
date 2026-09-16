package com.mirrorsoul.mirrorsoul_api.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "face-result")
public class FaceResultQueueProperties {
    @NotBlank
    private String queueUrl;
    @Min(0) @Max(20)
    private int waitTimeSeconds = 10;
    @Min(30) @Max(43200)
    private int visibilityTimeoutSeconds = 120;
}
