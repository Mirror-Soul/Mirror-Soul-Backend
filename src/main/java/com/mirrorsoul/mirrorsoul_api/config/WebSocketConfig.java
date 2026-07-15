package com.mirrorsoul.mirrorsoul_api.config;

import com.mirrorsoul.mirrorsoul_api.common.chat.ChatWebSocketHandler;
import com.mirrorsoul.mirrorsoul_api.common.chat.ChatWebSocketHandshakeInterceptor;
import com.mirrorsoul.mirrorsoul_api.common.webRTC.SignalingHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingHandler signalingHandler;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatWebSocketHandshakeInterceptor chatHandshakeInterceptor;

    @Value("${chat.websocket.allowed-origin-patterns:http://localhost:*}")
    private String[] chatAllowedOriginPatterns;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws/signaling")
                .setAllowedOrigins("*");

        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns(chatAllowedOriginPatterns);
    }

}
