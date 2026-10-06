package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.RagProfileJob;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobStatus;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiFaceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.FaceTrainingJobRepository;
import com.mirrorsoul.mirrorsoul_api.repository.RagProfileJobRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CloneStatusService {

    private static final String PENDING = "PENDING";
    private static final String PROCESSING = "PROCESSING";
    private static final String READY = "READY";
    private static final String FAILED = "FAILED";

    private final UserRepository userRepository;
    private final CloneRepository cloneRepository;
    private final VoiceTrainingJobRepository voiceTrainingJobRepository;
    private final FaceTrainingJobRepository faceTrainingJobRepository;
    private final RagProfileJobRepository ragProfileJobRepository;
    private final AiVoiceProfileRepository aiVoiceProfileRepository;
    private final AiFaceProfileRepository aiFaceProfileRepository;

    public ProfileResDTO.CloneStatusDTO getStatus(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        Clone clone = cloneRepository.findByUserUuid(userUuid).orElse(null);

        if (clone == null) {
            ProfileResDTO.CloneStatusComponentDTO pending = component(PENDING, null, null);
            return new ProfileResDTO.CloneStatusDTO(
                    PENDING,
                    new ProfileResDTO.CloneStatusComponentsDTO(pending, pending, pending, pending),
                    null
            );
        }

        ProfileResDTO.CloneStatusComponentDTO voice = voiceStatus(user, clone);
        ProfileResDTO.CloneStatusComponentDTO face = faceStatus(user, clone);
        ProfileResDTO.CloneStatusComponentDTO personality = personalityStatus(clone);
        ProfileResDTO.CloneStatusComponentDTO profileSummary = profileSummaryStatus(clone);

        return new ProfileResDTO.CloneStatusDTO(
                clone.getStatus(),
                new ProfileResDTO.CloneStatusComponentsDTO(voice, face, personality, profileSummary),
                clone.getVisibleSyncRate()
        );
    }

    private ProfileResDTO.CloneStatusComponentDTO voiceStatus(User user, Clone clone) {
        if (aiVoiceProfileRepository.existsByCloneIdAndActiveTrueAndStatus(clone.getId(), "ACTIVE")) {
            return component(READY, null, null);
        }
        return voiceTrainingJobRepository.findFirstByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .map(job -> component(mapVoiceStatus(job.getStatus()), job.getUpdatedAt(), job.getErrorCode()))
                .orElseGet(() -> component(PENDING, null, null));
    }

    private ProfileResDTO.CloneStatusComponentDTO faceStatus(User user, Clone clone) {
        if (aiFaceProfileRepository.existsByCloneIdAndActiveTrueAndStatus(clone.getId(), READY)) {
            return component(READY, null, null);
        }
        return faceTrainingJobRepository.findFirstByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .map(job -> component(mapFaceStatus(job.getStatus()), job.getUpdatedAt(), job.getErrorCode()))
                .orElseGet(() -> component(PENDING, null, null));
    }

    private ProfileResDTO.CloneStatusComponentDTO personalityStatus(Clone clone) {
        if (clone.isPersonalityTrainingCompleted()) {
            return component(READY, null, null);
        }
        return ragProfileJobRepository.findById(clone.getId())
                .map(this::personalityStatus)
                .orElseGet(() -> component(PENDING, null, null));
    }

    private ProfileResDTO.CloneStatusComponentDTO personalityStatus(RagProfileJob job) {
        if (job.getLastError() != null && !job.getLastError().isBlank()) {
            return component(FAILED, job.getNextAttemptAt(), job.getLastError());
        }
        String status = job.getRequestedRevision() > job.getDeliveredRevision() ? PROCESSING : PENDING;
        return component(status, job.getNextAttemptAt(), null);
    }

    private ProfileResDTO.CloneStatusComponentDTO profileSummaryStatus(Clone clone) {
        if (clone.getSummary() == null || clone.getSummary().isBlank()) {
            return component(PENDING, clone.getUpdatedAt(), null);
        }
        return component(READY, clone.getUpdatedAt(), null);
    }

    private String mapVoiceStatus(VoiceTrainingJobStatus status) {
        if (status == VoiceTrainingJobStatus.COMPLETED) return READY;
        if (status == VoiceTrainingJobStatus.FAILED) return FAILED;
        return status.name();
    }

    private String mapFaceStatus(FaceTrainingJobStatus status) {
        if (status == FaceTrainingJobStatus.COMPLETED) return READY;
        if (status == FaceTrainingJobStatus.FAILED) return FAILED;
        return status.name();
    }

    private ProfileResDTO.CloneStatusComponentDTO component(
            String status,
            LocalDateTime updatedAt,
            String errorCode
    ) {
        return new ProfileResDTO.CloneStatusComponentDTO(status, updatedAt, errorCode);
    }
}
