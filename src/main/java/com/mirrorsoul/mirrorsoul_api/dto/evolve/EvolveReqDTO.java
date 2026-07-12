package com.mirrorsoul.mirrorsoul_api.dto.evolve;

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
}
