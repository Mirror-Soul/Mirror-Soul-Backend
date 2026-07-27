package com.mirrorsoul.mirrorsoul_api.dto.push;

import com.mirrorsoul.mirrorsoul_api.domain.enums.PushDevicePlatform;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

public class PushDeviceResDTO {

    @Builder
    public record DeviceDTO(
            UUID installationId,
            PushDevicePlatform platform,
            boolean enabled,
            LocalDateTime lastSeenAt
    ) {
    }
}
