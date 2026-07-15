package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ChatMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByChatRoomIdAndSenderUuidAndClientMessageId(
            Long chatRoomId, UUID senderUuid, UUID clientMessageId);

    @Query("""
            select message from ChatMessage message
            join fetch message.sender
            where message.chatRoom.id = :roomId
              and (:beforeMessageId is null or message.id < :beforeMessageId)
            order by message.id desc
            """)
    List<ChatMessage> findMessagesBefore(
            @Param("roomId") Long roomId,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    @Query("""
            select message from ChatMessage message
            join fetch message.sender
            where message.id in :messageIds
            """)
    List<ChatMessage> findAllWithSenderByIdIn(@Param("messageIds") Collection<Long> messageIds);

    long countByChatRoomIdAndSenderUuidNotAndIdGreaterThan(
            Long chatRoomId, UUID senderUuid, Long lastReadMessageId);
}
