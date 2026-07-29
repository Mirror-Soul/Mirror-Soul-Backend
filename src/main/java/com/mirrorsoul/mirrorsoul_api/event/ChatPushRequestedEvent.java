package com.mirrorsoul.mirrorsoul_api.event;

import java.util.UUID;

public record ChatPushRequestedEvent(
        Long chatRoomId,
        Long messageId,
        UUID senderUserUuid,
        String senderName
) {
}
