package com.mirrorsoul.mirrorsoul_api.cloneprofile.dto;

import java.util.List;

public record CloneProfileGenerationInput(
        String selfIntroduction,
        String mbti,
        MbtiAxisScores mbtiAxisScores,
        List<InterviewAnswer> interviews,
        String valueBalanceSummary,
        String promptVersion
) {
}
