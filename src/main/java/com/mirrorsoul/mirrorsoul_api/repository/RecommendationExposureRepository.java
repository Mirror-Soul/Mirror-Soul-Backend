package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.RecommendationExposure;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationExposureRepository
        extends JpaRepository<RecommendationExposure, Long> {

    List<RecommendationExposure> findAllByRequesterIdAndTargetIdIn(
            Long requesterId,
            Collection<Long> targetIds
    );

    boolean existsByRequesterIdAndTargetIdAndLastExposedAtGreaterThanEqual(
            Long requesterId,
            Long targetId,
            LocalDateTime exposedSince
    );
}
