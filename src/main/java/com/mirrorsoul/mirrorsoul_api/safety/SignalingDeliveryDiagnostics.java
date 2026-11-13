package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalingDeliveryDiagnostics {

    private final VideoCallRepository calls;
    private final SafetyEvidenceService evidence;

    public void deliveryFailed(String roomId) {
        try {
            calls.findByRoomId(roomId).ifPresent(call -> evidence.diagnostic(call.getId(),
                    new SafetyDtos.Diagnostic(SafetyTypes.DiagnosticCode.CONNECTION_FAILED,
                            UUID.randomUUID().toString())));
        } catch (RuntimeException exception) {
            log.warn("Could not record signaling diagnostic exceptionType={}", exception.getClass().getSimpleName());
        }
    }
}
