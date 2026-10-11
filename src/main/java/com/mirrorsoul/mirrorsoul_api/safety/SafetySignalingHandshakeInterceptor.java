package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.config.AiInternalApiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
@Component @RequiredArgsConstructor
public class SafetySignalingHandshakeInterceptor implements HandshakeInterceptor {
    public static final String AI_ATTRIBUTE="safetyAuthenticatedAi";
    private final AiInternalApiProperties properties;
    @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes) {
        String supplied=request.getHeaders().getFirst("X-Internal-Api-Key");
        if(properties.matches(supplied) && properties.allowsSignaling()) {
            attributes.put(AI_ATTRIBUTE,true);return true;
        }
        return request.getPrincipal() instanceof Authentication authentication
            && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUserDetails details && details.isEnabled();
    }
    @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception) {}
}
