package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CloneProfileRefreshEventHandler {
    private final CloneProfileGenerationWorker worker;

    @Async("cloneProfileTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(CloneProfileRefreshRequestedEvent event) {
        worker.execute(event.jobId());
    }
}
