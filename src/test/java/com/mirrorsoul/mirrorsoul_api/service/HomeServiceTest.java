package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Sigungu;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredRegion;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.SigunguRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserPreferredSigunguRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserPreferredRegionRepository;
import com.mirrorsoul.mirrorsoul_api.region.NearbyRegionFinder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeServiceTest {

    private UserRepository userRepository;
    private UserPreferredSigunguRepository preferredSigunguRepository;
    private SigunguRepository sigunguRepository;
    private RegionRepository regionRepository;
    private UserPreferredRegionRepository preferredRegionRepository;
    private NearbyRegionFinder nearbyRegionFinder;
    private HomeService homeService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        preferredSigunguRepository = mock(UserPreferredSigunguRepository.class);
        sigunguRepository = mock(SigunguRepository.class);
        regionRepository = mock(RegionRepository.class);
        preferredRegionRepository = mock(UserPreferredRegionRepository.class);
        nearbyRegionFinder = mock(NearbyRegionFinder.class);
        homeService = new HomeService(
                userRepository,
                preferredSigunguRepository,
                sigunguRepository,
                regionRepository,
                preferredRegionRepository,
                nearbyRegionFinder
        );
    }

    @Test
    void getHomeReturnsRemainingTalkTimeAndLatestPreferredRegion() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        Region region = mock(Region.class);
        UserPreferredRegion preferredRegion = mock(UserPreferredRegion.class);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(user.getRemainingTalkTime()).thenReturn(9_000);
        when(preferredRegionRepository.findByUserId(1L)).thenReturn(Optional.of(preferredRegion));
        when(preferredRegion.getAnchorRegion()).thenReturn(region);
        when(preferredRegion.getNearbyCount()).thenReturn(3);
        when(region.getId()).thenReturn(10L);
        when(region.getSidoName()).thenReturn("서울특별시");
        when(region.getSigunguName()).thenReturn("강남구");
        when(region.getEupmyeondongName()).thenReturn("역삼동");
        when(nearbyRegionFinder.findNearestRegionIds(region, 3))
                .thenReturn(List.of(10L, 11L, 12L));

        HomeResDTO.HomeDTO result = homeService.getHome(userUuid);

        assertThat(result.remainingTalkTime().hours()).isEqualTo(2);
        assertThat(result.remainingTalkTime().minutes()).isEqualTo(30);
        assertThat(result.remainingTalkTime().seconds()).isZero();
        assertThat(result.preferredRegion().anchorRegionId()).isEqualTo(10L);
        assertThat(result.preferredRegion().eupmyeondongName()).isEqualTo("역삼동");
        assertThat(result.preferredRegion().includedRegionIds()).containsExactly(10L, 11L, 12L);
    }

    @Test
    void getHomeReturnsNullRegionAndZeroTimeWhenValuesAreUnavailable() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(user.getRemainingTalkTime()).thenReturn(-1);
        when(preferredRegionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        HomeResDTO.HomeDTO result = homeService.getHome(userUuid);

        assertThat(result.remainingTalkTime().hours()).isZero();
        assertThat(result.remainingTalkTime().minutes()).isZero();
        assertThat(result.remainingTalkTime().seconds()).isZero();
        assertThat(result.preferredRegion()).isNull();
    }

    @Test
    void getHomeThrowsWhenUserDoesNotExist() {
        UUID userUuid = UUID.randomUUID();
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeService.getHome(userUuid))
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.USER_NOT_FOUND)
                );

        verify(userRepository).findByUuid(userUuid);
    }

    @Test
    void updatePreferredRegionsReplacesPreferencesAndKeepsRequestOrder() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        Sigungu gangnam = sigungu(10L, "서울특별시", "강남구");
        Sigungu bundang = sigungu(20L, "경기도", "성남시 분당구");

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(sigunguRepository.findBySidoNameAndSigunguName("경기도", "성남시 분당구"))
                .thenReturn(Optional.of(bundang));
        when(sigunguRepository.findBySidoNameAndSigunguName("서울특별시", "강남구"))
                .thenReturn(Optional.of(gangnam));

        HomeResDTO.PreferredRegionsDTO result = homeService.updatePreferredRegions(
                userUuid,
                request(
                        region("경기도", "성남시 분당구"),
                        region("서울특별시", "강남구")
                )
        );

        assertThat(result.preferredRegions())
                .extracting(HomeResDTO.PreferredRegionDTO::sigunguId)
                .containsExactly(20L, 10L);
        verify(preferredSigunguRepository).deleteAllByUserId(1L);
        verify(preferredSigunguRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void updatePreferredRegionsRejectsDuplicateRegionsAfterTrimming() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        Sigungu gangnam = sigungu(10L, "서울특별시", "강남구");
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(sigunguRepository.findBySidoNameAndSigunguName("서울특별시", "강남구"))
                .thenReturn(Optional.of(gangnam));

        assertError(
                () -> homeService.updatePreferredRegions(
                        userUuid,
                        request(
                                region("서울특별시", "강남구"),
                                region(" 서울특별시 ", " 강남구 ")
                        )
                ),
                GeneralErrorCode.INVALID_PARAMETER
        );

        verify(preferredSigunguRepository, never()).deleteAllByUserId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatePreferredRegionsRejectsUnknownRegion() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(sigunguRepository.findBySidoNameAndSigunguName("없는시도", "없는시군구"))
                .thenReturn(Optional.empty());

        assertError(
                () -> homeService.updatePreferredRegions(
                        userUuid,
                        request(region("없는시도", "없는시군구"))
                ),
                GeneralErrorCode.REGION_NOT_FOUND
        );

        verify(preferredSigunguRepository, never()).deleteAllByUserId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatePreferredRegionsRejectsMoreThanThreeRegionsInServiceLayer() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));

        assertError(
                () -> homeService.updatePreferredRegions(
                        userUuid,
                        request(
                                region("시도1", "지역1"),
                                region("시도2", "지역2"),
                                region("시도3", "지역3"),
                                region("시도4", "지역4")
                        )
                ),
                GeneralErrorCode.INVALID_PARAMETER
        );

        verify(preferredSigunguRepository, never()).deleteAllByUserId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getSigunguOptionsReturnsIdsAndNamesInRepositoryOrder() {
        Sigungu gangnam = sigungu(10L, "서울특별시", "강남구");
        Sigungu songpa = sigungu(20L, "서울특별시", "송파구");
        when(sigunguRepository.findAllByOrderBySidoNameAscSigunguNameAsc())
                .thenReturn(List.of(gangnam, songpa));

        HomeResDTO.SigunguOptionsDTO result = homeService.getSigunguOptions();

        assertThat(result.regions())
                .extracting(HomeResDTO.PreferredRegionDTO::sigunguId)
                .containsExactly(10L, 20L);
    }

    private Sigungu sigungu(Long id, String sidoName, String sigunguName) {
        Sigungu sigungu = mock(Sigungu.class);
        when(sigungu.getId()).thenReturn(id);
        when(sigungu.getSidoName()).thenReturn(sidoName);
        when(sigungu.getSigunguName()).thenReturn(sigunguName);
        return sigungu;
    }

    private HomeReqDTO.UpdatePreferredRegionsDTO request(
            HomeReqDTO.PreferredRegionDTO... regions
    ) {
        return new HomeReqDTO.UpdatePreferredRegionsDTO(List.of(regions));
    }

    private HomeReqDTO.PreferredRegionDTO region(String sidoName, String sigunguName) {
        return new HomeReqDTO.PreferredRegionDTO(sidoName, sigunguName);
    }

    private void assertError(Runnable invocation, GeneralErrorCode expectedCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode)
                );
    }
}
