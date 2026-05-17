package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CloneRepository extends JpaRepository<Clone, Long> {

    @Query("select c.syncRate from Clone c where c.user.uuid = :userUuid")
    Optional<Integer> findSyncRateByUserUuid(@Param("userUuid") UUID userUuid);
}

