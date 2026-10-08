package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.FaceFile;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingSentence;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.event.VoiceTrainingJobRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.event.FaceTrainingJobRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.FaceFileRepository;
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
    private final FaceFileRepository faceFileRepository;
    private final FaceTrainingJobService faceTrainingJobService;
    private final ApplicationEventPublisher eventPublisher;

    public EvolveResDTO.twinSyncDTO twinSync(UUID uuid) {

        var clone = cloneRepository.findByUserUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));
        var syncRate = clone.getVisibleSyncRate();

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

    public EvolveResDTO.twinSyncDetailDTO twinSyncDetail(UUID uuid) {
        var clone = cloneRepository.findByUserUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));

        return EvolveResDTO.twinSyncDetailDTO.builder()
                .syncRate(clone.getVisibleSyncRate())
                .faceSimilarityScore(clone.getFaceSimilarityScore())
                .voiceSimilarityScore(clone.getVoiceSimilarityScore())
                .profileSimilarityScore(clone.getProfileSimilarityScore())
                .dataReliabilityScore(clone.getDataReliabilityScore())
                .similarityPenalty(clone.getSimilarityPenalty())
                .calculationVersion(clone.getSimilarityScoreVersion())
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

    @Transactional
    public EvolveResDTO.faceUpdateJobDTO completeFaceUpdate(
            UUID uuid,
            EvolveReqDTO.FaceUpdateCompleteDTO request
    ) {
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND, "User not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN,
                    "Only active users can submit face updates.");
        }
        cloneRepository.findByUserUuid(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));

        FileService.VerifiedS3Object uploadedFace = fileService.verifyFaceUpdateMediaAndBuildFileUrl(
                uuid, request.objectKey());
        FaceFile faceFile = faceFileRepository.findByUser_Id(user.getId())
                .map(existing -> {
                    existing.updateFile(uploadedFace.fileUrl(), uploadedFace.objectKey());
                    return existing;
                })
                .orElseGet(() -> faceFileRepository.save(
                        FaceFile.create(user, uploadedFace.fileUrl(), uploadedFace.objectKey())));

        FaceTrainingJob job = faceTrainingJobService.createPendingJob(
                user, faceFile, FaceTrainingJobSource.FACE_UPDATE);
        eventPublisher.publishEvent(new FaceTrainingJobRequestedEvent(job.getId()));

        return EvolveResDTO.faceUpdateJobDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .build();
    }

}
