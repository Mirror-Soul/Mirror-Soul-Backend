package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.config.AiInternalApiProperties;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class SafetySignalingHandshakeInterceptor implements HandshakeInterceptor {
    public static final String AI_ATTRIBUTE = "safetyAuthenticatedAi";

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final Pattern IPV4_ADDRESS = Pattern.compile(IPV4_OCTET + "(?:\\." + IPV4_OCTET + "){3}");

    private final AiInternalApiProperties properties;
    private final String legacyCallServerIp;
    private final boolean trustLoopbackProxy;

    public SafetySignalingHandshakeInterceptor(
            AiInternalApiProperties properties,
            @Value("${internal.ai.legacy-signaling-call-server-ip:}") String legacyCallServerIp,
            @Value("${internal.ai.legacy-signaling-trust-loopback-proxy:false}") boolean trustLoopbackProxy,
            @Value("${server.forward-headers-strategy:none}") String forwardHeadersStrategy
    ) {
        this.properties = properties;
        this.legacyCallServerIp = legacyCallServerIp.trim();
        this.trustLoopbackProxy = trustLoopbackProxy;
        if (this.legacyCallServerIp.isEmpty()) return;

        if (!IPV4_ADDRESS.matcher(this.legacyCallServerIp).matches()
                || this.legacyCallServerIp.startsWith("127.") || this.legacyCallServerIp.equals("0.0.0.0")) {
            throw new IllegalArgumentException("Legacy signaling requires one non-loopback IPv4 address.");
        }
        if (!"none".equalsIgnoreCase(forwardHeadersStrategy)) {
            throw new IllegalArgumentException("Legacy signaling requires server.forward-headers-strategy=none.");
        }
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String supplied = request.getHeaders().getFirst(API_KEY_HEADER);
        boolean validApiKey = properties.matches(supplied);
        boolean allowsAiSignaling = properties.allowsSignaling();
        if (validApiKey && allowsAiSignaling) {
            attributes.put(AI_ATTRIBUTE, true);
            return true;
        }
        if (request.getPrincipal() instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails details && details.isEnabled()) {
            return true;
        }
        if (allowsAiSignaling && supplied == null && request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null
                && isLegacyCallServer(request)) {
            attributes.put(AI_ATTRIBUTE, true);
            return true;
        }
        response.setStatusCode(validApiKey ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED);
        return false;
    }

    private boolean isLegacyCallServer(ServerHttpRequest request) {
        if (legacyCallServerIp.isEmpty()) return false;
        InetSocketAddress peer = request.getRemoteAddress();
        if (peer == null || peer.getAddress() == null) return false;

        if (peer.getAddress().isLoopbackAddress()) {
            // The on-box proxy must replace this header with the socket peer's address.
            List<String> realIp = request.getHeaders().get(REAL_IP_HEADER);
            return trustLoopbackProxy && realIp != null && realIp.size() == 1
                    && legacyCallServerIp.equals(realIp.get(0));
        }
        return legacyCallServerIp.equals(peer.getAddress().getHostAddress());
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}
