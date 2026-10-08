package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "push.firebase.enabled", havingValue = "true")
public class JobVerificationPushEventHandler {

    private final PushNotificationService pushNotificationService;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(JobVerificationReviewedEvent event) {
        try {
            pushNotificationService.sendJobVerificationResult(event);
        } catch (RuntimeException exception) {
            log.error("Failed to send job verification result push. requestId={}",
                    event.requestId(), exception);
        }
    }
}
