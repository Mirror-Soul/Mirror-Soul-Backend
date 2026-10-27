package com.mirrorsoul.mirrorsoul_api.dto.visual;

import java.util.List;
import java.util.UUID;

public record FaceTrainingMessageDTO(
        int schemaVersion,
        String jobType,
        Long jobId,
        String source,
        UUID userUuid,
        Long cloneId,
        String bucket,
        List<String> objectKeys,
        com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.Trace trace
) {
    public FaceTrainingMessageDTO(int schemaVersion,String jobType,Long jobId,String source,UUID userUuid,Long cloneId,String bucket,List<String> keys) {
        this(schemaVersion,jobType,jobId,source,userUuid,cloneId,bucket,keys,null);
    }
    public FaceTrainingMessageDTO withTrace(com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.Trace trace) {
        return new FaceTrainingMessageDTO(schemaVersion,jobType,jobId,source,userUuid,cloneId,bucket,objectKeys,trace);
    }
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String JOB_TYPE = "FACE_PROFILE_BUILD";

    public static FaceTrainingMessageDTO of(
            Long jobId,
            String source,
            UUID userUuid,
            Long cloneId,
            String bucket,
            List<String> objectKeys
    ) {
        return new FaceTrainingMessageDTO(
                CURRENT_SCHEMA_VERSION,
                JOB_TYPE,
                jobId,
                source,
                userUuid,
                cloneId,
                bucket,
                List.copyOf(objectKeys)
        );
    }
}
