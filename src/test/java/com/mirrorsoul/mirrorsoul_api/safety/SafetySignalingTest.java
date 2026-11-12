package com.mirrorsoul.mirrorsoul_api.safety;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.common.webRTC.SignalingMessage;
import com.mirrorsoul.mirrorsoul_api.config.AiInternalApiProperties;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.http.server.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketSession;
class SafetySignalingTest {
    private User user(long id) { return User.builder().id(id).status(UserStatus.ACTIVE).build(); }
    @Test void signalingRejectsAnonymousImpersonationAndRestrictionsButAllowsEndingCall() {
        User caller=user(1L),owner=user(2L);
        VideoCall call=mock(VideoCall.class);when(call.getId()).thenReturn(42L);when(call.getUser()).thenReturn(caller);
        when(call.getClone()).thenReturn(Clone.builder().user(owner).build());when(call.getStatus()).thenReturn(VideoCallStatus.READY);
        VideoCallRepository calls=mock(VideoCallRepository.class);when(calls.findByRoomId("call-abc")).thenReturn(Optional.of(call));
        SafetySignalingGuard guard=new SafetySignalingGuard(calls,mock(SafetyEvidenceService.class));
        WebSocketSession session=mock(WebSocketSession.class);when(session.getAttributes()).thenReturn(new HashMap<>());
        var offer=new SignalingMessage("OFFER","call-abc","signal:call-abc:caller","signal:call-abc:ai",Map.of());
        assertThat(guard.allowed(session,offer)).isFalse();
        when(session.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user(3L)),null,List.of()));
        assertThat(guard.allowed(session,offer)).isFalse();
        when(session.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(new CustomUserDetails(caller),null,List.of()));
        assertThat(guard.allowed(session,offer)).isTrue();
        caller.updateSafetyRestriction(LocalDateTime.now().plusDays(7));
        assertThat(guard.allowed(session,offer)).isFalse();
        assertThat(guard.allowed(session,new SignalingMessage("CALL_END","call-abc","signal:call-abc:caller","signal:call-abc:ai",Map.of("callId",42)))).isTrue();
        assertThat(guard.allowed(session,new SignalingMessage("CALL_END","call-abc","signal:call-abc:caller","signal:call-abc:ai",Map.of("callId",99)))).isFalse();
        caller.updateSafetyRestriction(null);caller.suspend();caller.unsuspend();
        assertThat(guard.allowed(session,offer)).isFalse();
        when(session.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(new CustomUserDetails(caller),null,List.of()));
        assertThat(guard.allowed(session,offer)).isTrue();
    }
    @Test void aiIdentityRequiresAuthenticatedInternalSession() {
        var guard=new SafetySignalingGuard(mock(VideoCallRepository.class),mock(SafetyEvidenceService.class));
        WebSocketSession session=mock(WebSocketSession.class);Map<String,Object> attrs=new HashMap<>();when(session.getAttributes()).thenReturn(attrs);
        var join=new SignalingMessage("JOIN",null,"ai-server","server",null);
        assertThat(guard.allowed(session,join)).isFalse();attrs.put(SafetySignalingHandshakeInterceptor.AI_ATTRIBUTE,true);
        assertThat(guard.allowed(session,join)).isTrue();
    }
    @Test void handshakeRequiresUserAuthenticationOrInternalApiKey() {
        var interceptor = new SafetySignalingHandshakeInterceptor(
                new AiInternalApiProperties("secret-key"), "", false, "none");
        MockHttpServletRequest request=new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        var successResponse = new ServletServerHttpResponse(new MockHttpServletResponse());
        request.addHeader("X-Internal-Api-Key","wrong");
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();
        request.removeHeader("X-Internal-Api-Key");request.addHeader("X-Internal-Api-Key","secret-key");Map<String,Object> attrs=new HashMap<>();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),successResponse,null,attrs)).isTrue();
        assertThat(attrs).containsEntry(SafetySignalingHandshakeInterceptor.AI_ATTRIBUTE,true);
        request.removeHeader("X-Internal-Api-Key");request.setUserPrincipal(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user(1L)),null,List.of()));
        Map<String, Object> memberAttributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, memberAttributes)).isTrue();
        assertThat(memberAttributes).doesNotContainKey(SafetySignalingHandshakeInterceptor.AI_ATTRIBUTE);
    }

    @Test void configuredCallServerCanJoinAsAiWithoutHeaders() {
        var interceptor = new SafetySignalingHandshakeInterceptor(
                new AiInternalApiProperties("secret-key"), "192.0.2.24", false, "none");
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.24");
        Map<String, Object> attributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes)).isTrue();
        assertThat(attributes).containsEntry(SafetySignalingHandshakeInterceptor.AI_ATTRIBUTE, true);

        var guard = new SafetySignalingGuard(mock(VideoCallRepository.class), mock(SafetyEvidenceService.class));
        var session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attributes);
        assertThat(guard.allowed(session, new SignalingMessage("JOIN", null, "ai-server", "server", null))).isTrue();

        request.addHeader("X-Internal-Api-Key", "wrong");
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();
        request.removeHeader("X-Internal-Api-Key");
        request.addHeader("Authorization", "Bearer invalid");
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();

        request.setUserPrincipal(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user(1L)), null, List.of()));
        Map<String, Object> memberAttributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, memberAttributes)).isTrue();
        assertThat(memberAttributes).doesNotContainKey(SafetySignalingHandshakeInterceptor.AI_ATTRIBUTE);
    }

    @Test void forwardedAddressIsAcceptedOnlyFromAnEnabledLoopbackProxy() {
        var properties = new AiInternalApiProperties("secret-key");
        var direct = new SafetySignalingHandshakeInterceptor(properties, "192.0.2.24", false, "none");
        var proxied = new SafetySignalingHandshakeInterceptor(properties, "192.0.2.24", true, "none");
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.30");
        request.addHeader("X-Real-IP", "192.0.2.24");
        assertThat(proxied.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();

        request.setRemoteAddr("127.0.0.1");
        assertThat(direct.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();
        Map<String, Object> attributes = new HashMap<>();
        assertThat(proxied.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes)).isTrue();
        assertThat(attributes).containsEntry(SafetySignalingHandshakeInterceptor.AI_ATTRIBUTE, true);

        request.addHeader("X-Real-IP", "198.51.100.30");
        assertThat(proxied.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();
    }

    @Test void legacyAndKeyedConnectionsBothRequireSignalingScope() {
        var properties = new AiInternalApiProperties("secret-key", null, null, Set.of("call_context"));
        var interceptor = new SafetySignalingHandshakeInterceptor(properties, "192.0.2.24", false, "none");
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.24");
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>())).isFalse();

        request.addHeader("X-Internal-Api-Key", "secret-key");
        var response = new MockHttpServletResponse();
        assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test void legacyCompatibilityRejectsBroadOrForwardedConfiguration() {
        var properties = new AiInternalApiProperties("secret-key");
        assertThatThrownBy(() -> new SafetySignalingHandshakeInterceptor(properties, "0.0.0.0/0", false, "none"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SafetySignalingHandshakeInterceptor(properties, "127.0.0.1", true, "none"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SafetySignalingHandshakeInterceptor(properties, "192.0.2.24", true, "framework"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
