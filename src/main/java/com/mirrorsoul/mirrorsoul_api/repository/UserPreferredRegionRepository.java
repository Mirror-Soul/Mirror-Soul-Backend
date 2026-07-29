package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredRegion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferredRegionRepository extends JpaRepository<UserPreferredRegion, Long> {

    @EntityGraph(attributePaths = {"user", "region"})
    List<UserPreferredRegion> findAllByUserIdIn(Collection<Long> userIds);
}
