package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.event.VoiceTrainingJobRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvolveService {

    private final CloneRepository cloneRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final VoiceTrainingJobService voiceTrainingJobService;
    private final ApplicationEventPublisher eventPublisher;

    public EvolveResDTO.twinSyncDTO twinSync(UUID uuid) {

        Integer syncRate = cloneRepository.findSyncRateByUserUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));

        return EvolveResDTO.twinSyncDTO.builder()
                .syncRate(syncRate)
                .build();
    }

    public EvolveResDTO.speechLineDTO speechLine(UUID uuid) {

        //문장 생성 로직
        String speechLine = "Dummy Data";

        return EvolveResDTO.speechLineDTO.builder()
                .speechLine(speechLine)
                .build();
    }

    @Transactional
    public EvolveResDTO.voiceUpdateJobDTO completeVoiceUpdate(
            UUID uuid,
            EvolveReqDTO.VoiceUpdateCompleteDTO request
    ) {
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND, "User not found."));

        FileService.VerifiedS3Object voiceUpdateAudio = fileService.verifyVoiceUpdateAudioAndBuildFileUrl(
                uuid,
                request.audioObjectKey()
        );

        VoiceTrainingJob voiceTrainingJob = voiceTrainingJobService.createPendingVoiceUpdateJob(
                user,
                voiceUpdateAudio.objectKey()
        );
        eventPublisher.publishEvent(new VoiceTrainingJobRequestedEvent(voiceTrainingJob.getId()));

        return EvolveResDTO.voiceUpdateJobDTO.builder()
                .jobId(voiceTrainingJob.getId())
                .status(voiceTrainingJob.getStatus().name())
                .build();
    }

}
