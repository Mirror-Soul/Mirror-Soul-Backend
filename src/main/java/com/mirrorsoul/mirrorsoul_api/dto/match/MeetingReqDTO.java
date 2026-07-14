package com.mirrorsoul.mirrorsoul_api.dto.match;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class MeetingReqDTO {
    public record CreateDTO(
            @NotNull(message = "상대방 UUID는 필수입니다.") UUID receiverUserUuid,
            @NotNull(message = "통화 ID는 필수입니다.") Long videoCallId,
            @NotBlank(message = "만남 신청 메시지는 필수입니다.")
            @Size(max = 1000, message = "만남 신청 메시지는 1000자 이하여야 합니다.") String message
    ) {
    }
}
