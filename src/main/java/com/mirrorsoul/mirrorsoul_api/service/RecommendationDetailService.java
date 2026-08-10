package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationDetailService {

    private static final String TWIN_AVAILABLE = "AVAILABLE";
    private static final String TWIN_IN_CALL = "IN_CALL";

    private final UserRepository userRepository;
    private final CloneRepository cloneRepository;
    private final AiVoiceProfileRepository aiVoiceProfileRepository;
    private final VideoCallRepository videoCallRepository;
    private final FileService fileService;

    public HomeResDTO.RecommendationDetailDTO getDetail(UUID targetUserUuid) {
        User target = userRepository.findByUuid(targetUserUuid)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> Boolean.TRUE.equals(user.getMatchingEnabled()))
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.RECOMMENDATION_TARGET_NOT_FOUND));
        Clone clone = cloneRepository.findByUserUuid(targetUserUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));

        boolean inCall = videoCallRepository.existsByCloneIdAndStatusIn(
                clone.getId(),
                List.of(VideoCallStatus.READY, VideoCallStatus.IN_PROGRESS)
        );

        return new HomeResDTO.RecommendationDetailDTO(
                target.getUuid(),
                target.getName(),
                calculateAge(target.getBirthDate()),
                target.getProfileImageUrl(),
                clone.getSyncRate(),
                toRegion(target.getResidenceRegion()),
                target.getSelfIntroduction(),
                inCall ? TWIN_IN_CALL : TWIN_AVAILABLE,
                findVoicePreview(clone)
        );
    }

    private HomeResDTO.VoicePreviewDTO findVoicePreview(Clone clone) {
        return aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(clone.getId())
                .filter(profile -> profile.getIntroAudioBucket() != null)
                .filter(profile -> profile.getIntroAudioObjectKey() != null)
                .map(this::toVoicePreview)
                .orElse(null);
    }

    private HomeResDTO.VoicePreviewDTO toVoicePreview(AiVoiceProfile profile) {
        return new HomeResDTO.VoicePreviewDTO(
                fileService.createPresignedDownloadUrl(
                        profile.getIntroAudioBucket(),
                        profile.getIntroAudioObjectKey()
                ),
                profile.getIntroAudioContentType(),
                profile.getIntroAudioDurationMs()
        );
    }

    private HomeResDTO.RegionDTO toRegion(Region region) {
        if (region == null) {
            return null;
        }
        return new HomeResDTO.RegionDTO(region.getSidoName(), region.getSigunguName());
    }

    private Integer calculateAge(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}
