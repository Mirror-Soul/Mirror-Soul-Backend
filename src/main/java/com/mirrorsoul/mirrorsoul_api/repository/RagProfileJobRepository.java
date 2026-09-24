package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.RagProfileJob;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RagProfileJobRepository extends JpaRepository<RagProfileJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from RagProfileJob j where j.cloneId = :id")
    Optional<RagProfileJob> findLockedById(@Param("id") Long id);

    @Query("""
            select j.cloneId from RagProfileJob j
            where j.requestedRevision > j.deliveredRevision and j.nextAttemptAt <= :now
            and (j.leaseUntil is null or j.leaseUntil <= :now)
            order by j.nextAttemptAt, j.cloneId
            """)
    List<Long> findDue(@Param("now") LocalDateTime now, Pageable pageable);
}
