package com.mirrorsoul.mirrorsoul_api.dto.push;

import com.mirrorsoul.mirrorsoul_api.domain.enums.PushDevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class PushDeviceReqDTO {

    public record RegisterDTO(
            @NotNull(message = "installationId는 필수입니다.")
            UUID installationId,

            @NotBlank(message = "pushToken은 필수입니다.")
            @Size(max = 512, message = "pushToken은 512자 이하여야 합니다.")
            String pushToken,

            @NotNull(message = "platform은 필수입니다.")
            PushDevicePlatform platform
    ) {
    }
}
