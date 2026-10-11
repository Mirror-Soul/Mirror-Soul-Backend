package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.jobreview.JobReviewLifecycleWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class JobVerificationPushEventHandler {
    private final JobReviewLifecycleWorker worker;
    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void handle(JobVerificationReviewedEvent event) { worker.deliver(event.requestId()); }
}
