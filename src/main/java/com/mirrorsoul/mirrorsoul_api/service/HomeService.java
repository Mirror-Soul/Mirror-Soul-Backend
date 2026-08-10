package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Sigungu;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredSigungu;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.SigunguRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserPreferredSigunguRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final int MIN_PREFERRED_REGION_COUNT = 1;
    private static final int MAX_PREFERRED_REGION_COUNT = 3;

    private final UserRepository userRepository;
    private final UserPreferredSigunguRepository preferredSigunguRepository;
    private final SigunguRepository sigunguRepository;

    public HomeResDTO.HomeDTO getHome(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        List<HomeResDTO.PreferredRegionDTO> preferredRegions = preferredSigunguRepository
                .findAllByUserIdOrderByCreatedAtAscIdAsc(user.getId()).stream()
                .map(userPreferredSigungu -> toPreferredRegion(userPreferredSigungu.getSigungu()))
                .toList();

        int remainingTalkTimeInSeconds = Math.max(
                user.getRemainingTalkTime() == null ? 0 : user.getRemainingTalkTime(),
                0
        );

        return new HomeResDTO.HomeDTO(
                toTalkTime(remainingTalkTimeInSeconds),
                preferredRegions
        );
    }

    @Transactional
    public HomeResDTO.PreferredRegionsDTO updatePreferredRegions(
            UUID userUuid,
            HomeReqDTO.UpdatePreferredRegionsDTO request
    ) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        List<HomeReqDTO.PreferredRegionDTO> requestedRegions = request.regions();

        if (requestedRegions == null
                || requestedRegions.size() < MIN_PREFERRED_REGION_COUNT
                || requestedRegions.size() > MAX_PREFERRED_REGION_COUNT) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "선호 지역은 1개 이상 3개 이하로 선택해야 합니다."
            );
        }

        List<Sigungu> orderedSigungu = new ArrayList<>(requestedRegions.size());
        HashSet<String> uniqueRegionKeys = new HashSet<>();
        for (HomeReqDTO.PreferredRegionDTO requestedRegion : requestedRegions) {
            if (requestedRegion == null
                    || requestedRegion.sidoName() == null
                    || requestedRegion.sigunguName() == null) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
            }

            String sidoName = requestedRegion.sidoName().trim();
            String sigunguName = requestedRegion.sigunguName().trim();
            if (sidoName.isEmpty() || sigunguName.isEmpty()) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
            }

            String regionKey = sidoName + "\u0000" + sigunguName;
            if (!uniqueRegionKeys.add(regionKey)) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "중복된 선호 지역을 선택할 수 없습니다."
                );
            }

            Sigungu sigungu = sigunguRepository
                    .findBySidoNameAndSigunguName(sidoName, sigunguName)
                    .orElseThrow(() -> new GeneralException(GeneralErrorCode.REGION_NOT_FOUND));
            orderedSigungu.add(sigungu);
        }

        preferredSigunguRepository.deleteAllByUserId(user.getId());
        List<UserPreferredSigungu> preferences = new ArrayList<>(orderedSigungu.size());
        for (Sigungu sigungu : orderedSigungu) {
            preferences.add(UserPreferredSigungu.builder()
                    .user(user)
                    .sigungu(sigungu)
                    .build());
        }
        preferredSigunguRepository.saveAll(preferences);

        return new HomeResDTO.PreferredRegionsDTO(
                orderedSigungu.stream().map(this::toPreferredRegion).toList()
        );
    }

    public HomeResDTO.SigunguOptionsDTO getSigunguOptions() {
        return new HomeResDTO.SigunguOptionsDTO(
                sigunguRepository.findAllByOrderBySidoNameAscSigunguNameAsc().stream()
                        .map(this::toPreferredRegion)
                        .toList()
        );
    }

    private HomeResDTO.TalkTimeDTO toTalkTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        return new HomeResDTO.TalkTimeDTO(hours, minutes, seconds);
    }

    private HomeResDTO.PreferredRegionDTO toPreferredRegion(Sigungu sigungu) {
        return new HomeResDTO.PreferredRegionDTO(
                sigungu.getId(),
                sigungu.getSidoName(),
                sigungu.getSigunguName()
        );
    }
}
