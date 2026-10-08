package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface JobVerificationRequestRepository extends JpaRepository<JobVerificationRequest, Long> {
    List<JobVerificationRequest> findByUser_IdAndStatus(Long userId, JobVerificationRequestStatus status);

    List<JobVerificationRequest> findByUser_IdIn(List<Long> userIds);

    @EntityGraph(attributePaths = "user")
    Page<JobVerificationRequest> findByStatus(JobVerificationRequestStatus status, Pageable pageable);

    Optional<JobVerificationRequest> findFirstByUser_UuidOrderByCreatedAtDescIdDesc(java.util.UUID uuid);

    @Query("""
            select request from JobVerificationRequest request
            where request.user.id in :userIds
              and not exists (
                  select 1 from JobVerificationRequest newer
                  where newer.user.id = request.user.id
                    and (newer.createdAt > request.createdAt
                         or (newer.createdAt = request.createdAt and newer.id > request.id))
              )
            """)
    List<JobVerificationRequest> findLatestForUserIds(@Param("userIds") Collection<Long> userIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from JobVerificationRequest request where request.id = :id")
    Optional<JobVerificationRequest> findByIdForUpdate(@Param("id") Long id);
}
