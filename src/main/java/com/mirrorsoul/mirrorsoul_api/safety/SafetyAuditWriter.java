package com.mirrorsoul.mirrorsoul_api.safety;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor
public class SafetyAuditWriter {
    private final SafetyEvidenceAuditRepository audits;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void access(Long reportId,Long actorId,String reason,boolean allowed) {
        audits.save(new SafetyEvidenceAudit(reportId,actorId,"VIEW",reason,allowed,SafetySupport.now()));
    }
}
