package com.mirrorsoul.mirrorsoul_api.dto.evolve;

import java.math.BigDecimal;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class EvolveResDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class twinSyncDTO {
        BigDecimal syncRate;
        Long voiceTrainingCount;
        LocalDateTime lastVoiceTrainingAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class speechLineDTO {
        Long sentenceId;
        String speechLine;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class voiceUpdateJobDTO {
        Long jobId;
        String status;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class faceUpdateJobDTO {
        Long jobId;
        String status;
    }

    public record valueBalanceQuestionDTO(
            Long questionId,
            ValueBalanceAxis axis,
            String leftLabel,
            String rightLabel,
            int currentSet,
            int answeredInSet,
            int setSize,
            int totalSets,
            int totalAnswered,
            boolean locked,
            LocalDateTime lockedUntil,
            boolean completed
    ) {}

    public record valueBalanceAnswerDTO(
            Long questionId,
            int currentSet,
            int answeredInSet,
            int setSize,
            int totalSets,
            int totalAnswered,
            boolean locked,
            LocalDateTime lockedUntil,
            boolean completed,
            Long analysisJobId
    ) {
    }
}
