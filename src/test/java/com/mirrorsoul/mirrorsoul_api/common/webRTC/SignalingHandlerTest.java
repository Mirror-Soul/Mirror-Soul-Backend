package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class SignalingHandlerTest {

    private ObjectMapper objectMapper;
    private WebSocketSessionRegistry registry;
    private SignalingHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        registry = new WebSocketSessionRegistry();
        handler = new SignalingHandler(objectMapper, registry);
    }

    @Test
    void unavailableAiServerReturnsCallRejectToSender() throws Exception {
        WebSocketSession sender = openSession("sender-session");
        registry.register("signal:call-abc:caller", sender);

        handler.handleMessage(sender, new TextMessage("""
                {
                  "type": "CALL_INVITE",
                  "roomId": "call-abc",
                  "from": "signal:call-abc:caller",
                  "to": "signal:call-abc:ai",
                  "data": {"callId": 42}
                }
                """));

        Map<?, ?> response = captureMessage(sender);
        Map<?, ?> data = (Map<?, ?>) response.get("data");

        assertThat(response.get("type")).isEqualTo("CALL_REJECT");
        assertThat(response.get("to")).isEqualTo("signal:call-abc:caller");
        assertThat(data.get("callId")).isEqualTo(42);
        assertThat(data.get("reason")).isEqualTo("AI_SERVER_UNAVAILABLE");
    }

    @Test
    void validCallInviteIsRelayedToAiServer() throws Exception {
        WebSocketSession sender = openSession("sender-session");
        WebSocketSession aiServer = openSession("ai-session");
        registry.register("signal:call-abc:caller", sender);
        registry.register("ai-server", aiServer);

        handler.handleMessage(sender, new TextMessage("""
                {
                  "type": "CALL_INVITE",
                  "roomId": "call-abc",
                  "from": "signal:call-abc:caller",
                  "to": "signal:call-abc:ai",
                  "data": {"callId": 42}
                }
                """));

        Map<?, ?> relayed = captureMessage(aiServer);
        assertThat(relayed.get("type")).isEqualTo("CALL_INVITE");
        assertThat(relayed.get("roomId")).isEqualTo("call-abc");
        assertThat(((Map<?, ?>) relayed.get("data")).keySet()).isEqualTo(java.util.Set.of("callId"));
    }

    @Test
    void aiServerCanRespondUsingRoomSpecificAiSignalId() throws Exception {
        WebSocketSession caller = openSession("caller-session");
        WebSocketSession aiServer = openSession("ai-session");
        registry.register("signal:call-abc:caller", caller);
        registry.register("ai-server", aiServer);

        handler.handleMessage(aiServer, new TextMessage("""
                {
                  "type": "CALL_ACCEPT",
                  "roomId": "call-abc",
                  "from": "signal:call-abc:ai",
                  "to": "signal:call-abc:caller",
                  "data": {"callId": 42}
                }
                """));

        Map<?, ?> relayed = captureMessage(caller);
        assertThat(relayed.get("type")).isEqualTo("CALL_ACCEPT");
        assertThat(relayed.get("from")).isEqualTo("signal:call-abc:ai");
    }

    @Test
    void callInviteRejectsLegacyCloneFields() throws Exception {
        WebSocketSession sender = openSession("sender-session");
        WebSocketSession aiServer = openSession("ai-session");
        registry.register("signal:call-abc:caller", sender);
        registry.register("ai-server", aiServer);

        handler.handleMessage(sender, new TextMessage("""
                {
                  "type": "CALL_INVITE",
                  "roomId": "call-abc",
                  "from": "signal:call-abc:caller",
                  "to": "signal:call-abc:ai",
                  "data": {
                    "callId": 42,
                    "cloneUserUuid": "5f0154ef-7d83-4ae4-a724-ae591e1c985e",
                    "mediaType": "VIDEO"
                  }
                }
                """));

        Map<?, ?> response = captureMessage(sender);
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        assertThat(response.get("type")).isEqualTo("SIGNALING_ERROR");
        assertThat(data.get("reason")).isEqualTo("INVALID_MESSAGE");
        verify(aiServer, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void messageWithMismatchedRoomSignalIdsIsRejected() throws Exception {
        WebSocketSession sender = openSession("sender-session");
        WebSocketSession aiServer = openSession("ai-session");
        registry.register("signal:call-other:caller", sender);
        registry.register("ai-server", aiServer);

        handler.handleMessage(sender, new TextMessage("""
                {
                  "type": "CALL_INVITE",
                  "roomId": "call-abc",
                  "from": "signal:call-other:caller",
                  "to": "signal:call-abc:ai",
                  "data": {"callId": 42}
                }
                """));

        Map<?, ?> response = captureMessage(sender);
        assertThat(response.get("type")).isEqualTo("SIGNALING_ERROR");
        verify(aiServer, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registeredCallerCannotClaimAnotherFromId() throws Exception {
        WebSocketSession sender = openSession("sender-session");
        WebSocketSession registeredCaller = openSession("registered-caller-session");
        WebSocketSession aiServer = openSession("ai-session");
        registry.register("signal:call-abc:caller", registeredCaller);
        registry.register("ai-server", aiServer);

        handler.handleMessage(sender, new TextMessage("""
                {
                  "type": "CALL_INVITE",
                  "roomId": "call-abc",
                  "from": "signal:call-abc:caller",
                  "to": "signal:call-abc:ai",
                  "data": {"callId": 42}
                }
                """));

        Map<?, ?> response = captureMessage(sender);
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        assertThat(response.get("type")).isEqualTo("SIGNALING_ERROR");
        assertThat(data.get("reason")).isEqualTo("INVALID_MESSAGE");
        verify(aiServer, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void offerRequiresOfferSdpType() throws Exception {
        WebSocketSession sender = openSession("sender-session");
        registry.register("signal:call-abc:caller", sender);

        handler.handleMessage(sender, new TextMessage("""
                {
                  "type": "OFFER",
                  "roomId": "call-abc",
                  "from": "signal:call-abc:caller",
                  "to": "signal:call-abc:ai",
                  "data": {
                    "callId": 42,
                    "sdp": {"type": "answer", "sdp": "v=0..."}
                  }
                }
                """));

        Map<?, ?> response = captureMessage(sender);
        assertThat(response.get("type")).isEqualTo("SIGNALING_ERROR");
    }

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn(id);
        return session;
    }

    private Map<?, ?> captureMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> responseCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(responseCaptor.capture());
        return objectMapper.readValue(responseCaptor.getValue().getPayload(), Map.class);
    }
}
