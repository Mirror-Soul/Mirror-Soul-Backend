package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.recommendation.UserEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEmbeddingEventHandler {

    private final UserEmbeddingService userEmbeddingService;

    @Async("embeddingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserEmbeddingRequestedEvent event) {
        try {
            userEmbeddingService.generate(event.userUuid(), event.type());
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to generate user embedding. userUuid={}, type={}",
                    event.userUuid(),
                    event.type(),
                    exception
            );
        }
    }
}
