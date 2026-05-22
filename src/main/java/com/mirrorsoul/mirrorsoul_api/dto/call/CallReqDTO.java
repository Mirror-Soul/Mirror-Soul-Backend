package com.mirrorsoul.mirrorsoul_api.dto.call;

import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CallReqDTO {

    public record StartCallDTO(
            @NotNull UUID callerUserUuid,
            CallMediaType mediaType
    ) {
    }

    public record EndCallDTO(
            String recordingUrl
    ) {
    }
}