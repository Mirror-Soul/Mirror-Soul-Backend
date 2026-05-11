package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredRegion;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.onboarding.OnboardingReqDTO;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserPreferredRegionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.FORBIDDEN;
import static com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.REGION_NOT_FOUND;
import static com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.USER_NOT_FOUND;
import static com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus.ONBOARD_A;
import static com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus.ONBOARD_B;

@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;
    private final MbtiProfileRepository mbtiProfileRepository;
    private final RegionRepository regionRepository;
    private final UserPreferredRegionRepository userPreferredRegionRepository;

    public void postProfile(OnboardingReqDTO.personaReqDTO req, Long userId, Job job) {

        User user = userRepository.findById(userId).orElseThrow(
                () -> new GeneralException(USER_NOT_FOUND));

        if (!ONBOARD_A.equals(user.getStatus())) {
            throw new GeneralException(FORBIDDEN, "ONBOARD_A 상태의 사용자만 페르소나를 저장할 수 있습니다.");
        }

        user.setName(req.getNickname());

        Region region = regionRepository.findBySidoNameAndSigunguNameAndEupmyeondongName(
                req.getSidoName(),
                req.getSigunguName(),
                req.getEupmyeondongName());

        if (region == null) {
            throw new GeneralException(REGION_NOT_FOUND);
        }

        userPreferredRegionRepository.save(UserPreferredRegion.builder()
                .user(user)
                .region(region)
                .build());

        user.setRegion(
                req.getSidoName() + " " + req.getSigunguName() + " " + req.getEupmyeondongName()
        );

        user.setJob(job);
        user.setJobDescription(req.getJobDescription());
        user.setJobCertificationObjectKey(req.getJobCertificationObjectKey());
        user.setStatus(ONBOARD_B);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Boolean checkDupNickname(OnboardingReqDTO.checkDupNicknameReqDTO req) {

        String nickname = req.getNickname();

        if (nickname == null || nickname.trim().isEmpty()) {
            return false;
        }
        return !userRepository.existsByName(nickname);
    }

    public void putPersonality(OnboardingReqDTO.personalityReqDTO req, Long userId) {

        User user = userRepository.findById(userId).orElseThrow(
                () -> new GeneralException(USER_NOT_FOUND));

        if (!ONBOARD_B.equals(user.getStatus())) {
            throw new GeneralException(FORBIDDEN, "ONBOARD_B 상태의 사용자만 성격 유형을 저장할 수 있습니다.");
        }

        mbtiProfileRepository.findByUser_Id(userId)
                .ifPresentOrElse(
                        mbtiProfile -> mbtiProfile.update(
                                req.getMbti(),
                                req.getIeScore(),
                                req.getNsScore(),
                                req.getFtScore(),
                                req.getPjScore()
                        ),
                        () -> mbtiProfileRepository.save(
                                MbtiProfile.create(
                                        user,
                                        req.getMbti(),
                                        req.getIeScore(),
                                        req.getNsScore(),
                                        req.getFtScore(),
                                        req.getPjScore()
                                )
                        )
                );

        user.setSelfIntroduction(req.getSelfIntroduction());
        user.setStatus(UserStatus.ONBOARD_C);
    }
}
