package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("WebSocket connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SignalingMessage signalingMessage =
                objectMapper.readValue(message.getPayload(), SignalingMessage.class);

        switch (signalingMessage.getType()) {
            case "JOIN" -> handleJoin(session, signalingMessage);
            case "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END",
                 "OFFER", "ANSWER", "ICE" -> relayMessage(signalingMessage);
            case "LEAVE" -> handleLeave(signalingMessage);
            default -> throw new IllegalArgumentException("Unknown signaling type: " + signalingMessage.getType());
        }
    }

    private void handleJoin(WebSocketSession session, SignalingMessage message) throws IOException {
        sessionRegistry.register(message.getFrom(), session);

        SignalingMessage joinedMessage = new SignalingMessage(
                "JOINED",
                message.getRoomId(),
                "server",
                message.getFrom(),
                "joined"
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(joinedMessage)));

        System.out.println("Joined signaling: " + message.getFrom());
    }

    private void relayMessage(SignalingMessage message) throws IOException {
        WebSocketSession receiverSession = sessionRegistry.getSession(message.getTo());

        if (receiverSession == null || !receiverSession.isOpen()) {
            System.out.println("Receiver not connected: " + message.getTo());
            return;
        }

        String payload = objectMapper.writeValueAsString(message);
        receiverSession.sendMessage(new TextMessage(payload));
    }

    private void handleLeave(SignalingMessage message) {
        sessionRegistry.remove(message.getFrom());

        System.out.println("Left signaling: " + message.getFrom());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        System.out.println("WebSocket closed: " + session.getId());
    }
}