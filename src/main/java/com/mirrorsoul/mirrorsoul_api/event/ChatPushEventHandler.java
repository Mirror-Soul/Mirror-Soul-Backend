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
public class ChatPushEventHandler {

    private final PushNotificationService pushNotificationService;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatPushRequestedEvent event) {
        try {
            pushNotificationService.sendChatMessage(event);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to send chat push. roomId={}, messageId={}",
                    event.chatRoomId(),
                    event.messageId(),
                    exception
            );
        }
    }
}
