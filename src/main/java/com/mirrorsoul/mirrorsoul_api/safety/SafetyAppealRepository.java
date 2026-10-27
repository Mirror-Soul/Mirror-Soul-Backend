package com.mirrorsoul.mirrorsoul_api.safety;
import org.springframework.data.jpa.repository.JpaRepository;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.AppealState;
import java.util.*;
public interface SafetyAppealRepository extends JpaRepository<SafetyAppeal,Long> {
    Optional<SafetyAppeal> findByDecisionId(Long decisionId);
    Optional<SafetyAppeal> findByReportIdAndState(Long reportId,AppealState state);
    List<SafetyAppeal> findByReportId(Long reportId);
    org.springframework.data.domain.Page<SafetyAppeal> findByUserId(Long userId,org.springframework.data.domain.Pageable pageable);
}
