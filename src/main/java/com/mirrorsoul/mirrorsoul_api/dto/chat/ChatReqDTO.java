package com.mirrorsoul.mirrorsoul_api.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class ChatReqDTO {
    public record SendMessageDTO(
            @NotNull(message = "clientMessageId는 필수입니다.") UUID clientMessageId,
            @NotBlank(message = "메시지 내용은 필수입니다.")
            @Size(max = 2000, message = "메시지는 2000자 이하여야 합니다.") String content
    ) {
    }

    public record ReadMessageDTO(
            @NotNull(message = "lastReadMessageId는 필수입니다.")
            @Positive(message = "lastReadMessageId는 양수여야 합니다.") Long lastReadMessageId
    ) {
    }
}

