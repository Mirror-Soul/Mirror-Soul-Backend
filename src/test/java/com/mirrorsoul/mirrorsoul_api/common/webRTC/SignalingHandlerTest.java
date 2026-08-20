package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class SignalingHandlerTest {

    @Test
    void unavailableAiServerReturnsCallRejectToSender() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SignalingHandler handler = new SignalingHandler(
                objectMapper,
                new WebSocketSessionRegistry()
        );
        WebSocketSession sender = mock(WebSocketSession.class);
        when(sender.isOpen()).thenReturn(true);
        when(sender.getId()).thenReturn("sender-session");

        handler.handleMessage(sender, new TextMessage("""
                {
                  "type": "CALL_INVITE",
                  "roomId": "room-1",
                  "from": "signal:user:caller",
                  "to": "signal:user:ai",
                  "data": {"callId": 42}
                }
                """));

        ArgumentCaptor<TextMessage> responseCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sender).sendMessage(responseCaptor.capture());
        Map<?, ?> response = objectMapper.readValue(responseCaptor.getValue().getPayload(), Map.class);
        Map<?, ?> data = (Map<?, ?>) response.get("data");

        assertThat(response.get("type")).isEqualTo("CALL_REJECT");
        assertThat(response.get("to")).isEqualTo("signal:user:caller");
        assertThat(data.get("callId")).isEqualTo(42);
        assertThat(data.get("reason")).isEqualTo("AI_SERVER_UNAVAILABLE");
    }
}
