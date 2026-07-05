package com.mirrorsoul.mirrorsoul_api.dto.evolve;

import jakarta.validation.constraints.NotBlank;

public class EvolveReqDTO {

    public record VoiceUpdateCompleteDTO(
            @NotBlank(message = "audioObjectKey is required.")
            String audioObjectKey,

            String speechLine,

            Double durationSeconds
    ) {
    }
}
