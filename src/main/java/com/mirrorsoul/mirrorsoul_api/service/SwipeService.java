package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.SwipeHistory;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.SwipeAction;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.SwipeHistoryRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirrorsoul.mirrorsoul_api.recommendation.RecommendationPolicy.SWIPE_REEXPOSURE_DAYS;

@Service
@RequiredArgsConstructor
public class SwipeService {

    private final UserRepository userRepository;
    private final SwipeHistoryRepository swipeHistoryRepository;

    @Transactional
    public void swipe(UUID swiperUuid, UUID targetUuid) {
        if (swiperUuid.equals(targetUuid)) {
            throw new GeneralException(GeneralErrorCode.SWIPE_SELF_NOT_ALLOWED);
        }

        User swiper = userRepository.findByUuid(swiperUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        User target = userRepository.findByUuid(targetUuid)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> Boolean.TRUE.equals(user.getMatchingEnabled()))
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.SWIPE_TARGET_UNAVAILABLE));

        boolean alreadySwiped = swipeHistoryRepository
                .existsBySwiperIdAndTargetIdAndCreatedAtGreaterThanEqual(
                        swiper.getId(),
                        target.getId(),
                        LocalDateTime.now().minusDays(SWIPE_REEXPOSURE_DAYS)
                );

        if (alreadySwiped) {
            return;
        }

        swipeHistoryRepository.save(SwipeHistory.builder()
                .swiper(swiper)
                .target(target)
                .action(SwipeAction.PASS)
                .build());
    }
}
