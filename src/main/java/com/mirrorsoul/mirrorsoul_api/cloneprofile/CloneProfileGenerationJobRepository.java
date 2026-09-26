package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CloneProfileGenerationJobRepository
        extends JpaRepository<CloneProfileGenerationJob, Long> {

    Optional<CloneProfileGenerationJob> findFirstByCloneIdAndStatusOrderByCreatedAtAsc(
            Long cloneId, CloneProfileGenerationJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from CloneProfileGenerationJob job join fetch job.clone clone join fetch clone.user where job.id = :id")
    Optional<CloneProfileGenerationJob> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select job.id from CloneProfileGenerationJob job
            where job.status = com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileGenerationJobStatus.PENDING
              and (job.nextAttemptAt is null or job.nextAttemptAt <= :now)
            order by job.createdAt
            """)
    List<Long> findDueJobIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            select job.id from CloneProfileGenerationJob job
            where job.status = com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileGenerationJobStatus.PROCESSING
              and job.startedAt < :cutoff
            """)
    List<Long> findAbandonedJobIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
