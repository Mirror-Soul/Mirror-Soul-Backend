package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.ClonePersonalityTag;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ClonePersonalityTagRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
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
public class MyProfileDetailService {

    private final UserRepository userRepository;
    private final CloneRepository cloneRepository;
    private final MbtiProfileRepository mbtiProfileRepository;
    private final ClonePersonalityTagRepository clonePersonalityTagRepository;
    private final AiVoiceProfileRepository aiVoiceProfileRepository;
    private final FileService fileService;
    private final JobVerificationDisplayService jobVerificationDisplayService;

    public ProfileResDTO.MyProfileDetailDTO getDetail(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        Clone clone = cloneRepository.findByUserUuid(userUuid).orElse(null);
        MbtiProfile mbtiProfile = mbtiProfileRepository.findByUser_Id(user.getId()).orElse(null);

        return new ProfileResDTO.MyProfileDetailDTO(
                user.getUuid(),
                user.getEmail(),
                user.getName(),
                calculateAge(user.getBirthDate()),
                profileImageUrl(user),
                clone == null ? null : clone.getVisibleSyncRate(),
                toRegion(user.getResidenceRegion()),
                user.getJob(),
                user.getJobDescription(),
                jobVerificationDisplayService.hasCurrentSubmission(user),
                jobVerificationDisplayService.documentReviewCompleted(user),
                user.getSelfIntroduction(),
                mbtiProfile == null ? null : mbtiProfile.getMbti(),
                toMbtiAxisScores(mbtiProfile),
                findPersonalityTags(clone),
                findVoicePreview(clone),
                Boolean.TRUE.equals(user.getMatchingEnabled())
        );
    }

    private List<String> findPersonalityTags(Clone clone) {
        if (clone == null) {
            return List.of();
        }
        return clonePersonalityTagRepository
                .findAllByCloneIdOrderByDisplayOrderAsc(clone.getId()).stream()
                .map(ClonePersonalityTag::getContent)
                .toList();
    }

    private String profileImageUrl(User user) {
        return fileService.createPresignedDownloadUrlOrFallback(
                user.getProfileImageObjectKey(),
                user.getProfileImageUrl()
        );
    }

    private ProfileResDTO.VoicePreviewDTO findVoicePreview(Clone clone) {
        if (clone == null) {
            return null;
        }
        return aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(clone.getId())
                .filter(profile -> profile.getIntroAudioBucket() != null)
                .filter(profile -> profile.getIntroAudioObjectKey() != null)
                .map(this::toVoicePreview)
                .orElse(null);
    }

    private ProfileResDTO.VoicePreviewDTO toVoicePreview(AiVoiceProfile profile) {
        return new ProfileResDTO.VoicePreviewDTO(
                fileService.createPresignedDownloadUrl(
                        profile.getIntroAudioBucket(),
                        profile.getIntroAudioObjectKey()
                ),
                profile.getIntroAudioContentType(),
                profile.getIntroAudioDurationMs()
        );
    }

    private ProfileResDTO.RegionDTO toRegion(Region region) {
        if (region == null) {
            return null;
        }
        return new ProfileResDTO.RegionDTO(region.getSidoName(), region.getSigunguName());
    }

    private ProfileResDTO.MbtiAxisScoresDTO toMbtiAxisScores(MbtiProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ProfileResDTO.MbtiAxisScoresDTO(
                profile.getIeScore(),
                profile.getNsScore(),
                profile.getFtScore(),
                profile.getPjScore()
        );
    }

    private Integer calculateAge(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}
