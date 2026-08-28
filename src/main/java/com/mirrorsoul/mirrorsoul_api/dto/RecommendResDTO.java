package com.mirrorsoul.mirrorsoul_api.dto;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import java.util.List;
import java.util.UUID;

public final class RecommendResDTO {

    private RecommendResDTO() {
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
            Job job,
            boolean jobCertificationSubmitted,
            ResidenceDTO residence,
            String selfIntroduction,
            MbtiType mbti,
            List<String> personalityTags,
            String profileImageUrl,
            int recommendationScore
    ) {
    }

    public record ResidenceDTO(
            String sidoName,
            String sigunguName
    ) {
    }
}
