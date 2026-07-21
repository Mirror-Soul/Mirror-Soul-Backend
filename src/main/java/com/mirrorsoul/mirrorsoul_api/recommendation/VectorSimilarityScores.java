package com.mirrorsoul.mirrorsoul_api.recommendation;

import java.util.UUID;

public record VectorSimilarityScores(
        UUID userUuid,
        Double jobScore,
        Double profileScore,
        Double cloneSummaryScore,
        Double conversationScore,
        Double interviewScore
) {
}
