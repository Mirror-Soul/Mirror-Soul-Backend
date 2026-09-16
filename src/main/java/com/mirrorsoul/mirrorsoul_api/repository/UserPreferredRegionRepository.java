package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredRegion;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferredRegionRepository
        extends JpaRepository<UserPreferredRegion, Long> {

    @EntityGraph(attributePaths = "anchorRegion")
    Optional<UserPreferredRegion> findByUserId(Long userId);
}
