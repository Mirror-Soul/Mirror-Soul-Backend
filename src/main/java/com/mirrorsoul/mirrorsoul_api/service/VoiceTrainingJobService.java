package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceTrainingJobService {

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
}
