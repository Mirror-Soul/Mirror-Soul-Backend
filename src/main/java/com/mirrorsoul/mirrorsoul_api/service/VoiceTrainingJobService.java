package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingSentence;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceTrainingJobService {

    private static final long VOICE_UPDATE_COOLDOWN_MINUTES = 2L;

    private final VoiceTrainingJobRepository voiceTrainingJobRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final AwsS3Properties awsS3Properties;

    public VoiceTrainingJob createPendingJob(User user) {
        List<InterviewRecord> records = interviewRecordRepository.findAllByUser_IdOrderByInterview_IdAsc(user.getId());

        VoiceTrainingJob job = VoiceTrainingJob.create(user);
        for (InterviewRecord record : records) {
            if (record.getAnswerAudioObjectKey() == null || record.getAnswerAudioObjectKey().isBlank()) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "Interview audio objectKey is missing."
                );
            }

            job.addFile(record, awsS3Properties.getBucket(), record.getAnswerAudioObjectKey());
        }

        return voiceTrainingJobRepository.save(job);
    }

    public VoiceTrainingJob createPendingVoiceUpdateJob(
            User user,
            VoiceTrainingSentence sentence,
            String audioObjectKey
    ) {
        assertVoiceUpdateSubmissionAllowed(user);

        VoiceTrainingJob job = VoiceTrainingJob.createVoiceUpdate(user, sentence);
        job.addFile(null, awsS3Properties.getBucket(), audioObjectKey);
        return voiceTrainingJobRepository.save(job);
    }

    public void assertVoiceUpdateSubmissionAllowed(User user) {
        boolean submittedRecently = voiceTrainingJobRepository.existsByUser_IdAndSourceAndCreatedAtAfter(
                user.getId(),
                VoiceTrainingJobSource.VOICE_UPDATE,
                LocalDateTime.now().minusMinutes(VOICE_UPDATE_COOLDOWN_MINUTES)
        );
        if (submittedRecently) {
            throw new GeneralException(
                    GeneralErrorCode.VOICE_TRAINING_TOO_FREQUENT,
                    "Voice training can be submitted once every 2 minutes."
            );
        }
    }
}
