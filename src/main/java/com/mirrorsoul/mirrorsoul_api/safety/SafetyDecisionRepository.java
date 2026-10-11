package com.mirrorsoul.mirrorsoul_api.safety;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface SafetyDecisionRepository extends JpaRepository<SafetyDecision,Long> {
    Optional<SafetyDecision> findByReportIdAndRequestKey(Long reportId,String key);
    Page<SafetyDecision> findByReportId(Long reportId,Pageable pageable);
}
