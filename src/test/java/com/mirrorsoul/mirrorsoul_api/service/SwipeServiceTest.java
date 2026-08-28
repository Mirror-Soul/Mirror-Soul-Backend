package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.SwipeHistory;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.SwipeAction;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.SwipeHistoryRepository;
import com.mirrorsoul.mirrorsoul_api.repository.RecommendationExposureRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserBlockRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SwipeServiceTest {

    private UserRepository userRepository;
    private SwipeHistoryRepository swipeHistoryRepository;
    private SwipeService swipeService;
    private RecommendationExposureRepository recommendationExposureRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        swipeHistoryRepository = mock(SwipeHistoryRepository.class);
        recommendationExposureRepository = mock(RecommendationExposureRepository.class);
        swipeService = new SwipeService(
                userRepository,
                swipeHistoryRepository,
                mock(UserBlockRepository.class),
                recommendationExposureRepository
        );
    }

    @Test
    void swipeSavesPassHistory() {
        UUID swiperUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        User swiper = user(1L, UserStatus.ACTIVE, true);
        User target = user(2L, UserStatus.ACTIVE, true);

        when(userRepository.findByUuid(swiperUuid)).thenReturn(Optional.of(swiper));
        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));
        allowExposure(1L, 2L);
        when(swipeHistoryRepository.existsBySwiperIdAndTargetIdAndCreatedAtGreaterThanEqual(
                any(), any(), any(LocalDateTime.class)
        )).thenReturn(false);

        swipeService.swipe(swiperUuid, targetUuid);

        ArgumentCaptor<SwipeHistory> captor = ArgumentCaptor.forClass(SwipeHistory.class);
        verify(swipeHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSwiper()).isSameAs(swiper);
        assertThat(captor.getValue().getTarget()).isSameAs(target);
        assertThat(captor.getValue().getAction()).isEqualTo(SwipeAction.PASS);
    }

    @Test
    void swipeIsIdempotentWithinReexposurePeriod() {
        UUID swiperUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        User swiper = user(1L, UserStatus.ACTIVE, true);
        User target = user(2L, UserStatus.ACTIVE, true);

        when(userRepository.findByUuid(swiperUuid)).thenReturn(Optional.of(swiper));
        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));
        allowExposure(1L, 2L);
        when(swipeHistoryRepository.existsBySwiperIdAndTargetIdAndCreatedAtGreaterThanEqual(
                any(), any(), any(LocalDateTime.class)
        )).thenReturn(true);

        swipeService.swipe(swiperUuid, targetUuid);

        verify(swipeHistoryRepository, never()).save(any(SwipeHistory.class));
    }

    @Test
    void swipeRejectsSelf() {
        UUID userUuid = UUID.randomUUID();

        assertError(
                () -> swipeService.swipe(userUuid, userUuid),
                GeneralErrorCode.SWIPE_SELF_NOT_ALLOWED
        );
        verify(userRepository, never()).findByUuid(any());
    }

    @Test
    void swipeRejectsUnavailableTarget() {
        UUID swiperUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        User swiper = user(1L, UserStatus.ACTIVE, true);
        User inactiveTarget = user(2L, UserStatus.INACTIVE, true);

        when(userRepository.findByUuid(swiperUuid)).thenReturn(Optional.of(swiper));
        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(inactiveTarget));

        assertError(
                () -> swipeService.swipe(swiperUuid, targetUuid),
                GeneralErrorCode.SWIPE_TARGET_UNAVAILABLE
        );
        verify(swipeHistoryRepository, never()).save(any(SwipeHistory.class));
    }

    @Test
    void swipeRejectsTargetThatWasNotExposed() {
        UUID swiperUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        User swiper = user(1L, UserStatus.ACTIVE, true);
        User target = user(2L, UserStatus.ACTIVE, true);

        when(userRepository.findByUuid(swiperUuid)).thenReturn(Optional.of(swiper));
        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));

        assertError(
                () -> swipeService.swipe(swiperUuid, targetUuid),
                GeneralErrorCode.SWIPE_TARGET_UNAVAILABLE
        );
        verify(swipeHistoryRepository, never()).save(any(SwipeHistory.class));
    }

    private void allowExposure(Long requesterId, Long targetId) {
        when(recommendationExposureRepository
                .existsByRequesterIdAndTargetIdAndLastExposedAtGreaterThanEqual(
                        org.mockito.ArgumentMatchers.eq(requesterId),
                        org.mockito.ArgumentMatchers.eq(targetId),
                        any(LocalDateTime.class)
                )).thenReturn(true);
    }

    private User user(Long id, UserStatus status, boolean matchingEnabled) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getStatus()).thenReturn(status);
        when(user.getMatchingEnabled()).thenReturn(matchingEnabled);
        return user;
    }

    private void assertError(Runnable invocation, GeneralErrorCode expectedCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode)
                );
    }
}
