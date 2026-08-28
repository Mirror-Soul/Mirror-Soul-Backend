package com.mirrorsoul.mirrorsoul_api.dto.home;

import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import java.util.List;
import java.util.UUID;

public final class HomeResDTO {

    private HomeResDTO() {
    }

    public record HomeDTO(
            TalkTimeDTO remainingTalkTime,
            List<PreferredRegionDTO> preferredRegions
    ) {
    }

    public record TalkTimeDTO(
            int hours,
            int minutes,
            int seconds
    ) {
    }

    public record PreferredRegionDTO(
            Long sigunguId,
            String sidoName,
            String sigunguName
    ) {
    }

    public record PreferredRegionsDTO(
            List<PreferredRegionDTO> preferredRegions
    ) {
    }

    public record SigunguOptionsDTO(
            List<PreferredRegionDTO> regions
    ) {
    }

    public record RecommendationSliceDTO(
            List<RecommendationDTO> recommendations,
            int page,
            int size,
            boolean hasNext
    ) {
    }

    public record RecommendationDTO(
            UUID userUuid,
            String name,
            Integer age,
            String profileImageUrl,
            PreferredRegionDTO region,
            int recommendationScore
    ) {
    }

    public record RecommendationDetailDTO(
            UUID userUuid,
            String name,
            Integer age,
            String profileImageUrl,
            Integer syncRate,
            RegionDTO region,
            Job job,
            boolean jobCertificationSubmitted,
            String selfIntroduction,
            MbtiType mbti,
            MbtiAxisScoresDTO mbtiAxisScores,
            List<String> personalityTags,
            VoicePreviewDTO voicePreview
    ) {
    }

    public record RegionDTO(
            String sidoName,
            String sigunguName
    ) {
    }

    public record MbtiAxisScoresDTO(
            Integer ieScore,
            Integer nsScore,
            Integer ftScore,
            Integer pjScore
    ) {
    }

    public record VoicePreviewDTO(
            String audioUrl,
            String contentType,
            Integer durationMs
    ) {
    }
}
