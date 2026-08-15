package com.mirrorsoul.mirrorsoul_api.dto.evolve;

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
        Integer syncRate;
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

    public record valueBalanceQuestionDTO(
            Long questionId,
            ValueBalanceAxis axis,
            String leftLabel,
            String rightLabel,
            int answeredCount,
            int dailyLimit
    ) {
        public boolean dailyLimitReached() {
            return answeredCount >= dailyLimit;
        }
    }

    public record valueBalanceAnswerDTO(
            Long questionId,
            int answeredCount,
            int dailyLimit
    ) {
    }
}
