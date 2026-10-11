package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.FaceFile;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.repository.FaceTrainingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceTrainingJobService {

    private final FaceTrainingJobRepository faceTrainingJobRepository;
    private final AwsS3Properties awsS3Properties;

    public FaceTrainingJob createPendingJob(User user, FaceFile faceFile, FaceTrainingJobSource source) {
        if (!user.isAiWorkAllowed(java.time.LocalDateTime.now())) {
            throw new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(
                com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.SAFETY_RESTRICTED);
        }
        FaceTrainingJob job = FaceTrainingJob.create(user, source);
        job.addFile(faceFile, awsS3Properties.getBucket(), faceFile.getObjectKey());
        return faceTrainingJobRepository.save(job);
    }
}
