package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiCallContextDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiCallContextService {

    private static final int SCHEMA_VERSION = 1;
    private static final String READY_CLONE_STATUS = "READY";
    private static final String ACTIVE_VOICE_STATUS = "ACTIVE";
    private static final String VOICE_PROVIDER = "ELEVENLABS";
    private static final EnumSet<VideoCallStatus> AVAILABLE_CALL_STATUSES =
            EnumSet.of(VideoCallStatus.READY, VideoCallStatus.IN_PROGRESS);

    private final VideoCallRepository videoCallRepository;
    private final MbtiProfileRepository mbtiProfileRepository;
    private final AiVoiceProfileRepository aiVoiceProfileRepository;

    public AiCallContextDTO getContext(Long callId) {
        VideoCall call = videoCallRepository.findByIdWithParticipants(callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));

        if (!AVAILABLE_CALL_STATUSES.contains(call.getStatus())) {
            throw new GeneralException(GeneralErrorCode.INVALID_CALL_STATUS);
        }

        Clone clone = call.getClone();
        if (!READY_CLONE_STATUS.equals(clone.getStatus())) {
            throw new GeneralException(GeneralErrorCode.CLONE_NOT_READY);
        }

        User cloneOwner = clone.getUser();
        MbtiType mbti = mbtiProfileRepository.findByUser_Id(cloneOwner.getId())
                .map(MbtiProfile::getMbti)
                .orElse(null);

        AiVoiceProfile voiceProfile = aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueAndStatusOrderByCreatedAtDescIdDesc(
                        clone.getId(),
                        ACTIVE_VOICE_STATUS
                )
                .filter(profile -> hasText(profile.getElevenlabsVoiceId()))
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.VOICE_PROFILE_NOT_READY));

        return new AiCallContextDTO(
                SCHEMA_VERSION,
                call.getId(),
                call.getRoomId(),
                call.getMediaType(),
                call.getStatus(),
                new AiCallContextDTO.CloneContext(
                        clone.getId(),
                        cloneOwner.getUuid(),
                        new AiCallContextDTO.PersonaContext(
                                cloneOwner.getName(),
                                cloneOwner.getGender(),
                                cloneOwner.getBirthDate(),
                                cloneOwner.getJob(),
                                cloneOwner.getJobDescription(),
                                cloneOwner.getSelfIntroduction(),
                                mbti
                        ),
                        new AiCallContextDTO.VoiceContext(
                                voiceProfile.getId(),
                                voiceProfile.getVoiceTrainingJob().getId(),
                                VOICE_PROVIDER,
                                voiceProfile.getElevenlabsVoiceId()
                        )
                )
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
