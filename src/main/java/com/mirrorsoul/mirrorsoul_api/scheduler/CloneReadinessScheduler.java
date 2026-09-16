package com.mirrorsoul.mirrorsoul_api.scheduler;

import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.service.CloneReadinessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloneReadinessScheduler {
    private final CloneRepository clones;
    private final CloneReadinessService readiness;

    // Covers voice profiles committed after the face event, including external writers.
    @Scheduled(fixedDelayString = "${clone-training.readiness-refresh-ms:60000}",
            initialDelayString = "${clone-training.readiness-refresh-ms:60000}")
    public void refresh() {
        long afterId = 0;
        while (true) {
            var ids = clones.findIdsAfter(afterId, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try {
                    readiness.refresh(id);
                } catch (RuntimeException exception) {
                    log.error("Could not refresh clone readiness. cloneId={}", id, exception);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
    }
}
