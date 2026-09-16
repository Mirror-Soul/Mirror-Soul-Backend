package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CloneRepository extends JpaRepository<Clone, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clone c where c.id = :id")
    Optional<Clone> findLockedById(@Param("id") Long id);

    @Query("select c.id from Clone c where c.id > :afterId order by c.id")
    List<Long> findIdsAfter(@Param("afterId") Long afterId, Pageable pageable);

    @Query("select c.syncRate from Clone c where c.user.uuid = :userUuid")
    Optional<Integer> findSyncRateByUserUuid(@Param("userUuid") UUID userUuid);

    Optional<Clone> findByUserUuid(UUID userUuid);

    @Query("""
            select clone
            from Clone clone
            join fetch clone.user user
            where user.uuid in :userUuids
            """)
    List<Clone> findAllByUserUuidIn(@Param("userUuids") Collection<UUID> userUuids);
}
