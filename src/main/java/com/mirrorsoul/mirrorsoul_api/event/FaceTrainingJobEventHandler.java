package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.service.FaceTrainingJobPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FaceTrainingJobEventHandler {

    private final FaceTrainingJobPublisher faceTrainingJobPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FaceTrainingJobRequestedEvent event) {
        faceTrainingJobPublisher.publish(event.faceTrainingJobId());
    }
}
