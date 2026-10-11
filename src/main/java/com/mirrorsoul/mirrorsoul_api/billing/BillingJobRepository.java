package com.mirrorsoul.mirrorsoul_api.billing;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BillingJobRepository extends JpaRepository<BillingJob, Long> {
    Optional<BillingJob> findByEventKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from BillingJob j where j.id = :id")
    Optional<BillingJob> lock(@Param("id") Long id);
    @Query("select j.id from BillingJob j where (j.state = 'PENDING' and j.nextAttemptAt <= :now) or (j.state = 'PROCESSING' and j.leaseUntil <= :now) order by j.id")
    List<Long> available(@Param("now") LocalDateTime now, Pageable pageable);
    Page<BillingJob> findByPaymentIdOrderByIdDesc(Long paymentId, Pageable pageable);
    long countByState(BillingTypes.JobState state);
    @Query("select min(j.createdAt) from BillingJob j where j.state <> 'COMPLETED'")
    LocalDateTime oldestUnfinished();
}
