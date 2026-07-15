package com.mirrorsoul.mirrorsoul_api.dto.chat;

import com.mirrorsoul.mirrorsoul_api.domain.enums.ChatMessageType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

public class ChatResDTO {
    @Builder
    public record RoomListDTO(int totalCount, List<RoomDTO> rooms) {
    }

    @Builder
    public record RoomDTO(
            Long chatRoomId,
            PartnerDTO partner,
            MessageDTO lastMessage,
            long unreadCount,
            LocalDateTime createdAt
    ) {
    }

    @Builder
    public record PartnerDTO(
            UUID userUuid,
            String name,
            String profileImageUrl,
            Integer age,
            Integer twinSimilarity,
            LocalDateTime lastActiveAt
    ) {
    }

    @Builder
    public record MessageListDTO(
            List<MessageDTO> messages,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    @Builder
    public record MessageDTO(
            Long messageId,
            Long chatRoomId,
            UUID senderUserUuid,
            UUID clientMessageId,
            ChatMessageType messageType,
            String content,
            LocalDateTime createdAt
    ) {
    }

    @Builder
    public record ReadResultDTO(
            Long chatRoomId,
            Long lastReadMessageId,
            LocalDateTime lastReadAt,
            boolean updated
    ) {
    }
}

