package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.service.VoiceTrainingJobPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class VoiceTrainingJobEventHandler {

    private final VoiceTrainingJobPublisher voiceTrainingJobPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VoiceTrainingJobRequestedEvent event) {
        voiceTrainingJobPublisher.publish(event.voiceTrainingJobId());
    }
}
