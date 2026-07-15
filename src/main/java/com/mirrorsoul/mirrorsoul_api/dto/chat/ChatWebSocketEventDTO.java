package com.mirrorsoul.mirrorsoul_api.dto.chat;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatWebSocketEventDTO(
        String type,
        Long chatRoomId,
        LocalDateTime occurredAt,
        Object data
) {
    public record MessageReadData(
            UUID readerUserUuid,
            Long lastReadMessageId,
            LocalDateTime readAt
    ) {
    }
}

