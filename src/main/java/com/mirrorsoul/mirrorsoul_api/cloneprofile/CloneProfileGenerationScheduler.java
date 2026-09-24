package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloneProfileGenerationScheduler {
    private final CloneProfileGenerationJobService jobService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${clone-profile.generation.recovery-delay-ms:30000}")
    public void recoverAndDispatch() {
        for (Long jobId : jobService.findAbandonedJobs()) {
            jobService.recoverAbandoned(jobId);
        }
        for (Long jobId : jobService.findDueJobs()) {
            eventPublisher.publishEvent(new CloneProfileRefreshRequestedEvent(jobId));
        }
    }
}
