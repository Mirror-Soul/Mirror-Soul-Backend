package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingSentence;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.event.VoiceTrainingJobRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingSentenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvolveService {

    private final CloneRepository cloneRepository;
    private final UserRepository userRepository;
    private final VoiceTrainingSentenceRepository voiceTrainingSentenceRepository;
    private final VoiceTrainingJobRepository voiceTrainingJobRepository;
    private final FileService fileService;
    private final VoiceTrainingJobService voiceTrainingJobService;
    private final ApplicationEventPublisher eventPublisher;

    public EvolveResDTO.twinSyncDTO twinSync(UUID uuid) {

        Integer syncRate = cloneRepository.findSyncRateByUserUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));

        VoiceTrainingJobSource voiceUpdate = VoiceTrainingJobSource.VOICE_UPDATE;
        long voiceTrainingCount = voiceTrainingJobRepository.countByUser_UuidAndSource(uuid, voiceUpdate);
        LocalDateTime lastVoiceTrainingAt = voiceTrainingJobRepository
                .findFirstByUser_UuidAndSourceOrderByCreatedAtDescIdDesc(uuid, voiceUpdate)
                .map(VoiceTrainingJob::getCreatedAt)
                .orElse(null);

        return EvolveResDTO.twinSyncDTO.builder()
                .syncRate(syncRate)
                .voiceTrainingCount(voiceTrainingCount)
                .lastVoiceTrainingAt(lastVoiceTrainingAt)
                .build();
    }

    public EvolveResDTO.speechLineDTO speechLine(UUID uuid) {
        List<Long> recentlyUsedSentenceIds = voiceTrainingJobRepository
                .findTop5ByUser_UuidAndSourceAndVoiceTrainingSentenceIsNotNullOrderByCreatedAtDescIdDesc(
                        uuid,
                        VoiceTrainingJobSource.VOICE_UPDATE
                )
                .stream()
                .map(VoiceTrainingJob::getVoiceTrainingSentence)
                .map(VoiceTrainingSentence::getId)
                .distinct()
                .toList();

        VoiceTrainingSentence sentence = findNextVoiceTrainingSentence(recentlyUsedSentenceIds)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.SERVICE_UNAVAILABLE,
                        "No active voice training sentence is available."
                ));

        return EvolveResDTO.speechLineDTO.builder()
                .sentenceId(sentence.getId())
                .speechLine(sentence.getContent())
                .build();
    }

    private Optional<VoiceTrainingSentence> findNextVoiceTrainingSentence(
            List<Long> recentlyUsedSentenceIds
    ) {
        if (recentlyUsedSentenceIds.isEmpty()) {
            return voiceTrainingSentenceRepository.findRandomActive();
        }

        return voiceTrainingSentenceRepository.findRandomActiveExcluding(recentlyUsedSentenceIds)
                .or(voiceTrainingSentenceRepository::findRandomActive);
    }

    @Transactional
    public EvolveResDTO.voiceUpdateJobDTO completeVoiceUpdate(
            UUID uuid,
            EvolveReqDTO.VoiceUpdateCompleteDTO request
    ) {
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND, "User not found."));

        VoiceTrainingSentence sentence = voiceTrainingSentenceRepository
                .findByIdAndActiveTrue(request.sentenceId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "Voice training sentence not found or inactive."
                ));

        FileService.VerifiedS3Object voiceUpdateAudio = fileService.verifyVoiceUpdateAudioAndBuildFileUrl(
                uuid,
                request.audioObjectKey()
        );

        VoiceTrainingJob voiceTrainingJob = voiceTrainingJobService.createPendingVoiceUpdateJob(
                user,
                sentence,
                voiceUpdateAudio.objectKey()
        );
        eventPublisher.publishEvent(new VoiceTrainingJobRequestedEvent(voiceTrainingJob.getId()));

        return EvolveResDTO.voiceUpdateJobDTO.builder()
                .jobId(voiceTrainingJob.getId())
                .status(voiceTrainingJob.getStatus().name())
                .build();
    }

}
