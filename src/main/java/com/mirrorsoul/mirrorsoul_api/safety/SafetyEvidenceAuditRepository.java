package com.mirrorsoul.mirrorsoul_api.safety;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SafetyEvidenceAuditRepository extends JpaRepository<SafetyEvidenceAudit,Long> {
    org.springframework.data.domain.Page<SafetyEvidenceAudit> findByReportId(Long reportId,org.springframework.data.domain.Pageable pageable);
}
