package com.mirrorsoul.mirrorsoul_api.quality;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @Slf4j
public class QualityMonitor {
    private final TrainingReadService training;
    @Value("${quality.training-stall-seconds:900}") private long stallSeconds;
    @Scheduled(fixedDelayString="${quality.monitor-ms:60000}",initialDelayString="${quality.monitor-ms:60000}")
    public void inspect() {
        long stalled=training.countStalled(LocalDateTime.now().minusSeconds(stallSeconds));
        if(stalled>0)log.error("Training jobs stalled count={}",stalled);
        long failed=training.countUnresolvedFailures();
        if(failed>0)log.error("Training jobs failed count={}",failed);
    }
}
