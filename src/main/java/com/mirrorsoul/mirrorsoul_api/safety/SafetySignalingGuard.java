package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.common.webRTC.SignalingMessage;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;
@Slf4j @Service @RequiredArgsConstructor
public class SafetySignalingGuard {
    private final VideoCallRepository calls;
    private final SafetyEvidenceService evidence;
    @Transactional(readOnly=true)
    public boolean allowed(WebSocketSession session,SignalingMessage message) {
        boolean ai=Boolean.TRUE.equals(session.getAttributes().get(SafetySignalingHandshakeInterceptor.AI_ATTRIBUTE));
        if("ai-server".equals(message.getFrom())) return ai && Set.of("JOIN","LEAVE").contains(message.getType());
        VideoCall call=calls.findByRoomId(message.getRoomId()).orElse(null);
        if(call==null) return false;
        boolean aiSender=message.getFrom().endsWith(":ai");
        if(aiSender) { if(!ai) return false; }
        else if(!(session.getPrincipal() instanceof Authentication auth) || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails user) || !Objects.equals(user.getId(),call.getUser().getId())
                || !Objects.equals(user.getSessionVersion(),call.getUser().getSessionVersion())) return false;
        if(message.getData() instanceof Map<?,?> data && data.get("callId")!=null) {
            try { if(!Objects.equals(Long.valueOf(data.get("callId").toString()),call.getId())) return false; }
            catch(NumberFormatException e) { return false; }
        }
        if(Set.of("CALL_END","CALL_REJECT","LEAVE").contains(message.getType())) return true;
        return Set.of(VideoCallStatus.READY,VideoCallStatus.IN_PROGRESS).contains(call.getStatus())
            && call.getUser().isAiWorkAllowed(SafetySupport.now()) && call.getClone().getUser().isAiWorkAllowed(SafetySupport.now());
    }
    public void deliveryFailed(String roomId) {
        try {
            calls.findByRoomId(roomId).ifPresent(call -> evidence.diagnostic(call.getId(),
                new SafetyDtos.Diagnostic(SafetyTypes.DiagnosticCode.CONNECTION_FAILED,UUID.randomUUID().toString())));
        } catch(RuntimeException e) { log.warn("Could not record signaling diagnostic exceptionType={}",e.getClass().getSimpleName()); }
    }
}
