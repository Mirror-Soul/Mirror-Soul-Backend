package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.SwipeHistory;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SwipeHistoryRepository extends JpaRepository<SwipeHistory, Long> {

    boolean existsBySwiperIdAndTargetIdAndCreatedAtGreaterThanEqual(
            Long swiperId,
            Long targetId,
            LocalDateTime createdAt
    );
}
