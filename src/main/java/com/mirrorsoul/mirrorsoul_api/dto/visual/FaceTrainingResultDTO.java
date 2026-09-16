package com.mirrorsoul.mirrorsoul_api.dto.visual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FaceTrainingResultDTO(String eventType, Long jobId, UUID userUuid,
        Long cloneId, String status, Result result, Failure error) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String profileStatus, Artifacts artifacts, Boolean qualityGatePassed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artifacts(String bucket, String profileKey, String portraitKey,
            String manifestKey, String previewKey) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Failure(String code, String message, Boolean retryable) {}
}
