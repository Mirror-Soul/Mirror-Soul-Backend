package com.mirrorsoul.mirrorsoul_api.repository;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob.State;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;
public interface MemberDeletionJobRepository extends JpaRepository<MemberDeletionJob,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MemberDeletionJob> findByAiStateAndAiLeaseUntilLessThanEqual(State state,LocalDateTime now,Pageable pageable);
    @Query("select j.memberId from MemberDeletionJob j where j.id=:id")
    Optional<Long> findMemberId(@Param("id") Long id);
    Optional<MemberDeletionJob> findByMemberId(Long memberId);
    @Query("select j.id from MemberDeletionJob j where j.memberId=:memberId")
    Optional<Long> findIdByMemberId(@Param("memberId") Long memberId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from MemberDeletionJob j where j.id=:id")
    Optional<MemberDeletionJob> findForUpdate(@Param("id") Long id);
    @Query("select j from MemberDeletionJob j where j.backendState in :states and j.backendAttempts < j.backendAttemptLimit")
    List<MemberDeletionJob> findBackendAvailable(@Param("states") List<State> states,Pageable pageable);
    @Query("select j from MemberDeletionJob j where j.aiAttempts < j.aiAttemptLimit and (j.aiState in :states or (j.aiState=:processing and j.aiLeaseUntil <= :now))")
    Page<MemberDeletionJob> findAvailable(@Param("states") List<State> states,@Param("processing") State processing,
        @Param("now") LocalDateTime now,Pageable pageable);
}
