package com.mirrorsoul.mirrorsoul_api.dto.call;

import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import lombok.Builder;

public class CallResDTO {

    @Builder
    public record StartCallDTO(
            Long callId,
            String roomId,
            CallMediaType mediaType,
            VideoCallStatus status,
            String callerSignalId,
            String aiSignalId,
            String signalingUrl
    ) {
    }

    @Builder
    public record EndCallDTO(
            Long callId,
            VideoCallStatus status,
            Integer durationSec
    ) {
    }
}
