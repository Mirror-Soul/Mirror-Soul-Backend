package com.mirrorsoul.mirrorsoul_api.common.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.event.ChatRealtimeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeEventListener {
    private final ObjectMapper objectMapper;
    private final ChatWebSocketSessionRegistry sessionRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatRealtimeEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.payload());
            event.recipients().forEach(recipient -> sessionRegistry.send(recipient, payload));
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize chat realtime event", exception);
        }
    }
}

