package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Gender;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.RecommendResDTO;
import com.mirrorsoul.mirrorsoul_api.recommendation.UserEmbeddingRepository;
import com.mirrorsoul.mirrorsoul_api.region.NearbyRegionFinder;
import com.mirrorsoul.mirrorsoul_api.repository.ClonePersonalityTagRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.RecommendationExposureRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserPreferredRegionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;

class RecommendServiceTest {

    @Test
    void getRecommendationsReturnsEmptyFirstPage() {
        UserRepository userRepository = mock(UserRepository.class);
        UserPreferredRegionRepository preferredRegionRepository =
                mock(UserPreferredRegionRepository.class);
        NearbyRegionFinder nearbyRegionFinder = mock(NearbyRegionFinder.class);
        RecommendationScoreCalculator scoreCalculator = mock(RecommendationScoreCalculator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserEmbeddingRepository> embeddingRepositoryProvider =
                mock(ObjectProvider.class);
        MbtiProfileRepository mbtiProfileRepository = mock(MbtiProfileRepository.class);
        ClonePersonalityTagRepository personalityTagRepository =
                mock(ClonePersonalityTagRepository.class);
        RecommendationExposureRepository exposureRepository =
                mock(RecommendationExposureRepository.class);
        FileService fileService = mock(FileService.class);
        RecommendService service = new RecommendService(
                userRepository,
                preferredRegionRepository,
                nearbyRegionFinder,
                scoreCalculator,
                embeddingRepositoryProvider,
                mbtiProfileRepository,
                personalityTagRepository,
                exposureRepository,
                fileService
        );

        UUID requesterUuid = UUID.randomUUID();
        User requester = mock(User.class);
        when(requester.getId()).thenReturn(1L);
        when(requester.getUuid()).thenReturn(requesterUuid);
        when(requester.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(requester.getBirthDate()).thenReturn(LocalDate.now().minusYears(25));
        when(requester.getGender()).thenReturn(Gender.MALE);
        when(userRepository.findByUuid(requesterUuid)).thenReturn(Optional.of(requester));
        when(preferredRegionRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findRecommendationCandidates(
                eq(1L),
                eq(Gender.MALE),
                eq(true),
                any(LocalDate.class),
                any(LocalDateTime.class),
                eq(false),
                anyList()
        )).thenReturn(List.of());

        RecommendResDTO.RecommendationSliceDTO result = service.getRecommendations(
                requesterUuid,
                PageRequest.of(0, 10)
        );

        assertThat(result.recommendations()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.hasNext()).isFalse();
        verify(exposureRepository, never()).saveAll(any());
        verify(userRepository).findRecommendationCandidates(
                eq(1L),
                eq(Gender.MALE),
                eq(true),
                any(LocalDate.class),
                any(LocalDateTime.class),
                anyBoolean(),
                anyList()
        );
    }
}
