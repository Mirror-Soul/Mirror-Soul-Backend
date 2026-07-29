package com.mirrorsoul.mirrorsoul_api.dto;

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
            String profileImageUrl,
            int recommendationScore
    ) {
    }
}
