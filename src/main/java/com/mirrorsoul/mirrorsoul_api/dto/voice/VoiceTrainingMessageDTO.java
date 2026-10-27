package com.mirrorsoul.mirrorsoul_api.dto.voice;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VoiceTrainingMessageDTO(
        String jobType,
        String source,
        Long jobId,
        UUID userUuid,
        String bucket,
        List<String> audioObjectKeys,
        OffsetDateTime requestedAt,
        com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.Trace trace
) {
    public VoiceTrainingMessageDTO(String jobType,String source,Long jobId,UUID userUuid,String bucket,List<String> keys,OffsetDateTime at) {
        this(jobType,source,jobId,userUuid,bucket,keys,at,null);
    }
    public VoiceTrainingMessageDTO withTrace(com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.Trace trace) {
        return new VoiceTrainingMessageDTO(jobType,source,jobId,userUuid,bucket,audioObjectKeys,requestedAt,trace);
    }
    public static VoiceTrainingMessageDTO of(
            String source,
            Long jobId,
            UUID userUuid,
            String bucket,
            List<String> audioObjectKeys
    ) {
        return new VoiceTrainingMessageDTO(
                "VOICE_TRAINING",
                source,
                jobId,
                userUuid,
                bucket,
                audioObjectKeys,
                OffsetDateTime.now()
        );
    }
}
