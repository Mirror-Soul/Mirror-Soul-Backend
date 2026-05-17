package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }

    public void remove(String userId) {
        sessions.remove(userId);
    }

    public boolean contains(String userId) {
        return sessions.containsKey(userId);
    }
}