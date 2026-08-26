package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.service.ValueBalanceAnalysisJobPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ValueBalanceAnalysisEventHandler {
    private final ValueBalanceAnalysisJobPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ValueBalanceAnalysisRequestedEvent event) {
        publisher.publish(event.jobId());
    }
}
