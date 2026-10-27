package com.mirrorsoul.mirrorsoul_api.safety;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Slf4j @Component @RequiredArgsConstructor
public class SafetyEvidenceCleanupScheduler {
    private final SafetyEvidenceService evidence;
    @Scheduled(fixedDelayString="${safety.evidence.cleanup-delay-ms:3600000}")
    public void purgeExpiredEvidence() {
        try {
            int count=0;
            for(int batch=0;batch<10;batch++) {
                int purged=evidence.purgeExpired();count+=purged;
                if(purged<100) break;
            }
            if(count>0) log.info("Expired safety evidence purged count={}",count);
        } catch(Exception e) { log.error("Safety evidence cleanup failed",e); }
    }
}
