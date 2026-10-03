package com.mirrorsoul.mirrorsoul_api.dto.voice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VoiceTrainingResultDTO(String eventType, Long jobId, UUID userUuid,
        Long cloneId, String status, Result result, Failure error) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String elevenlabsVoiceId, BigDecimal voiceScore, IntroAudio introAudio) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntroAudio(String bucket, String objectKey, String contentType,
            Long sizeBytes, Integer durationMs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Failure(String code, String message, Boolean retryable) {}
}
