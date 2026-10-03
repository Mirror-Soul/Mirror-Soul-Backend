package com.mirrorsoul.mirrorsoul_api.dto.call;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Gender;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import java.time.LocalDate;
import java.util.UUID;

public record AiCallContextDTO(
        int schemaVersion,
        Long callId,
        String roomId,
        CallMediaType mediaType,
        VideoCallStatus status,
        @JsonProperty("clone") CloneContext cloneContext
) {

    public record CloneContext(
            Long cloneId,
            UUID userUuid,
            PersonaContext persona,
            VoiceContext voice
    ) {
    }

    public record PersonaContext(
            String name,
            Gender gender,
            LocalDate birthDate,
            Job job,
            String jobDescription,
            String selfIntroduction,
            MbtiType mbti
    ) {
    }

    public record VoiceContext(
            Long voiceProfileId,
            Long voiceTrainingJobId,
            String provider,
            String voiceId
    ) {
    }
}
