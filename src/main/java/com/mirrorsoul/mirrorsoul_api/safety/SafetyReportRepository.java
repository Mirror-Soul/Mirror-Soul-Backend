package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;
public interface SafetyReportRepository extends JpaRepository<SafetyReport,Long>,JpaSpecificationExecutor<SafetyReport> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from SafetyReport r where r.id=:id")
    Optional<SafetyReport> findForUpdate(@Param("id") Long id);
    @Query("select r.targetId from SafetyReport r where r.id=:id") Optional<Long> findTargetId(@Param("id") Long id);
    Optional<SafetyReport> findByReporterIdAndRequestKey(Long reporterId,String key);
    Optional<SafetyReport> findFirstByReporterIdAndContextHashAndCreatedAtAfterOrderByIdDesc(Long reporterId,String hash,LocalDateTime cutoff);
    long countByReporterIdAndCreatedAtAfter(Long reporterId,LocalDateTime cutoff);
    Page<SafetyReport> findByReporterId(Long reporterId,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SafetyReport> findByEvidencePurgedFalseAndEvidenceExpiresAtLessThanEqual(LocalDateTime now,Pageable pageable);
}
