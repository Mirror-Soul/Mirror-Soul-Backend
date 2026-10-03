package com.mirrorsoul.mirrorsoul_api.dto.call;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Speaker;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AiTalkLogReqDTO {

    public record Save(
            @NotNull(message = "eventId는 필수입니다.")
            UUID eventId,

            @NotNull(message = "speaker는 필수입니다.")
            Speaker speaker,

            @NotBlank(message = "message는 필수입니다.")
            @Size(max = 2000, message = "message는 2000자 이하여야 합니다.")
            String message,

            @NotNull(message = "startedAt은 필수입니다.")
            OffsetDateTime startedAt,

            OffsetDateTime endedAt
    ) {
    }
}
