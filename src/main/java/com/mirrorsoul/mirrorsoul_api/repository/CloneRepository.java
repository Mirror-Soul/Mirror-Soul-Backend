package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CloneRepository extends JpaRepository<Clone, Long> {

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
