package com.mirrorsoul.mirrorsoul_api.dto.visual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FaceTrainingResultDTO(String eventType, Long jobId, UUID userUuid,
        Long cloneId, String status, Result result, Failure error) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String profileStatus, Artifacts artifacts, Boolean qualityGatePassed,
            BigDecimal faceScore, CloneSimilarity cloneSimilarity) {
        public Result(String profileStatus, Artifacts artifacts, Boolean qualityGatePassed) {
            this(profileStatus, artifacts, qualityGatePassed, null, null);
        }

        public Result(String profileStatus, Artifacts artifacts, Boolean qualityGatePassed,
                BigDecimal faceScore) {
            this(profileStatus, artifacts, qualityGatePassed, faceScore, null);
        }

        public BigDecimal similarityFaceScore() {
            return cloneSimilarity == null ? faceScore : cloneSimilarity.faceScore();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloneSimilarity(String calculationVersion, BigDecimal faceScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artifacts(String bucket, String profileKey, String portraitKey,
            String manifestKey, String previewKey) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Failure(String code, String message, Boolean retryable) {}
}
