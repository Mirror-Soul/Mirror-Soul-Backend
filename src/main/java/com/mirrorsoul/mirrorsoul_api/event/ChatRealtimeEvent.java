package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatWebSocketEventDTO;
import java.util.Set;
import java.util.UUID;

public record ChatRealtimeEvent(
        Set<UUID> recipients,
        ChatWebSocketEventDTO payload
) {
}
