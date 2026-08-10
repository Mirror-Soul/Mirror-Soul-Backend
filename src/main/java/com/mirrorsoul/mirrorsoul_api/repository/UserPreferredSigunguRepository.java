package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredSigungu;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface UserPreferredSigunguRepository
        extends JpaRepository<UserPreferredSigungu, Long> {

    @EntityGraph(attributePaths = "sigungu")
    List<UserPreferredSigungu> findAllByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    @EntityGraph(attributePaths = "sigungu")
    Optional<UserPreferredSigungu> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Modifying(flushAutomatically = true)
    void deleteAllByUserId(Long userId);
}
