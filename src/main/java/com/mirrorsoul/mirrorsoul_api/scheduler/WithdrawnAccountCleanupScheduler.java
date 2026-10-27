package com.mirrorsoul.mirrorsoul_api.scheduler;

import com.mirrorsoul.mirrorsoul_api.service.WithdrawnAccountCleanupService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnAccountCleanupScheduler {

    private final WithdrawnAccountCleanupService cleanupService;
    private final com.mirrorsoul.mirrorsoul_api.service.MemberDeletionService deletion;

    @Scheduled(cron = "${account.cleanup.cron:0 0 3 * * *}", zone = "${account.cleanup.zone:Asia/Seoul}")
    public void anonymizeExpiredAccounts() {
        int count = cleanupService.anonymizeExpiredAccounts(LocalDateTime.now());
        if (count > 0) {
            log.info("Confirmed expired withdrawn accounts. count={}", count);
        }
    }

    @Scheduled(fixedDelayString = "${account.cleanup.retry-delay-ms:60000}")
    public void processDeletionJobs() {
        deletion.expireAiLeases(LocalDateTime.now());
        for (Long id : deletion.pendingBackend()) {
            try {
                deletion.cleanBackend(id,LocalDateTime.now());
            } catch (Exception e) {
                log.error("Backend deletion job failed jobId={}",id,e);
                deletion.recordFailure(id,LocalDateTime.now());
            }
        }
    }
}
