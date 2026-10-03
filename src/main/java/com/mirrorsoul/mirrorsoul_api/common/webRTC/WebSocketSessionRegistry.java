package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String id, WebSocketSession session) {
        sessions.put(id, session);
    }

    public WebSocketSession getSession(String id) {
        return sessions.get(id);
    }

    public boolean isRegisteredAs(String id, WebSocketSession session) {
        WebSocketSession registered = sessions.get(id);
        return registered != null && registered.getId().equals(session.getId());
    }

    public void remove(String id) {
        sessions.remove(id);
    }

    public void remove(WebSocketSession session) {
        sessions.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
    }
}
