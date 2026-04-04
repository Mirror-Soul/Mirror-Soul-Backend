package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredRegion;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.dto.onboarding.OnboardingReqDTO;
import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserPreferredRegionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.REGION_NOT_FOUND;
import static com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final UserPreferredRegionRepository userPreferredRegionRepository;

    public void postProfile(OnboardingReqDTO.personaReqDTO req, Long userId, Job job) {

        User user = userRepository.findById(userId).orElseThrow(
                () -> new GeneralException(USER_NOT_FOUND));

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
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Boolean checkDupNickname(OnboardingReqDTO.checkDupNicknameReqDTO req) {

        String nickname = req.getNickname();

        // null or 빈값 방어 (선택)
        if (nickname == null || nickname.trim().isEmpty()) {
            return false;
        }
        return !userRepository.existsByName(nickname);
    }

}
