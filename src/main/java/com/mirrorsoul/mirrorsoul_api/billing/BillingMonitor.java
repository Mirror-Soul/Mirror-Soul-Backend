package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.repository.TalkTimeTransactionRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor @Slf4j
public class BillingMonitor {
    private final TalkTimeTransactionRepository entries;
    private final BillingJobRepository jobs;
    @Scheduled(fixedDelayString = "${billing.monitor-interval-ms:60000}")
    @Transactional(readOnly = true)
    public void inspect() {
        long mismatches = entries.countBalanceMismatches();
        if (mismatches > 0) log.error("Billing reconciliation mismatch count={}", mismatches);
        long dead = jobs.countByState(BillingTypes.JobState.DEAD);
        if (dead > 0) log.error("Billing dead jobs detected count={}", dead);
        var oldest = jobs.oldestUnfinished();
        if (oldest != null && Duration.between(oldest, LocalDateTime.now()).getSeconds() > 300)
            log.error("Billing queue delayed oldestCreatedAt={}", oldest);
    }
}
