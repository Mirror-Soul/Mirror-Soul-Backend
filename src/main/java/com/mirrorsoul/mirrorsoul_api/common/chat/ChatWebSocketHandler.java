package com.mirrorsoul.mirrorsoul_api.common.chat;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatWebSocketSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID userUuid = userUuid(session);
        if (userUuid == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication is required"));
            return;
        }
        sessionRegistry.register(userUuid, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userUuid = userUuid(session);
        if (userUuid != null) {
            sessionRegistry.remove(userUuid, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        UUID userUuid = userUuid(session);
        if (userUuid != null) {
            sessionRegistry.remove(userUuid, session);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private UUID userUuid(WebSocketSession session) {
        Object attribute = session.getAttributes()
                .get(ChatWebSocketHandshakeInterceptor.USER_UUID_ATTRIBUTE);
        return attribute instanceof UUID uuid ? uuid : null;
    }
}

