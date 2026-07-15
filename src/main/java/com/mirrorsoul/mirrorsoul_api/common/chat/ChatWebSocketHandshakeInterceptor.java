package com.mirrorsoul.mirrorsoul_api.common.chat;

import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
public class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_UUID_ATTRIBUTE = "chatUserUuid";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request.getPrincipal() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            log.warn("Rejected unauthenticated chat WebSocket handshake");
            return false;
        }
        attributes.put(USER_UUID_ATTRIBUTE, userDetails.getUuid());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No post-handshake work is required.
    }
}
