package com.mirrorsoul.mirrorsoul_api.common.chat;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ChatWebSocketSessionRegistry {
    private final Map<UUID, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(UUID userUuid, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userUuid, ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
    }

    public void remove(UUID userUuid, WebSocketSession session) {
        Map<String, WebSocketSession> userSessions = sessionsByUser.get(userUuid);
        if (userSessions == null) {
            return;
        }
        userSessions.remove(session.getId());
        if (userSessions.isEmpty()) {
            sessionsByUser.remove(userUuid, userSessions);
        }
    }

    public void send(UUID userUuid, String payload) {
        Map<String, WebSocketSession> userSessions = sessionsByUser.get(userUuid);
        if (userSessions == null) {
            return;
        }

        for (WebSocketSession session : userSessions.values()) {
            if (!session.isOpen()) {
                remove(userUuid, session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException exception) {
                remove(userUuid, session);
                try {
                    session.close();
                } catch (IOException ignored) {
                    // The failed session has already been removed.
                }
            }
        }
    }
}

