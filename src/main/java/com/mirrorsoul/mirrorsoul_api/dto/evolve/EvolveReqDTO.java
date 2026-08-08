package com.mirrorsoul.mirrorsoul_api.dto.evolve;

import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceChosenSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EvolveReqDTO {

    public record VoiceUpdateCompleteDTO(
            @NotNull(message = "sentenceId is required.")
            Long sentenceId,

            @NotBlank(message = "audioObjectKey is required.")
            String audioObjectKey,

            Double durationSeconds
    ) {
    }

    public record ValueBalanceAnswerDTO(
            @NotNull(message = "chosenSide is required.")
            ValueBalanceChosenSide chosenSide
    ) {
    }
}
