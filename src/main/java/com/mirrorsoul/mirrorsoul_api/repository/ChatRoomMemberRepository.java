package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ChatRoomMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    @EntityGraph(attributePaths = {"chatRoom", "user"})
    @Query("""
            select member from ChatRoomMember member
            where member.user.uuid = :userUuid
              and member.leftAt is null
            order by coalesce(member.chatRoom.lastMessageAt, member.chatRoom.createdAt) desc,
                     member.chatRoom.id desc
            """)
    List<ChatRoomMember> findAllActiveByUserUuid(@Param("userUuid") UUID userUuid);

    @EntityGraph(attributePaths = {"chatRoom", "user"})
    @Query("""
            select member from ChatRoomMember member
            where member.chatRoom.id = :roomId
              and member.user.uuid = :userUuid
              and member.leftAt is null
            """)
    Optional<ChatRoomMember> findActiveByRoomIdAndUserUuid(
            @Param("roomId") Long roomId, @Param("userUuid") UUID userUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"chatRoom", "user"})
    @Query("""
            select member from ChatRoomMember member
            where member.chatRoom.id = :roomId
              and member.user.uuid = :userUuid
              and member.leftAt is null
            """)
    Optional<ChatRoomMember> findActiveByRoomIdAndUserUuidForUpdate(
            @Param("roomId") Long roomId, @Param("userUuid") UUID userUuid);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select member from ChatRoomMember member
            where member.chatRoom.id in :roomIds
              and member.leftAt is null
            """)
    List<ChatRoomMember> findAllActiveByRoomIdIn(@Param("roomIds") Collection<Long> roomIds);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select member from ChatRoomMember member
            where member.chatRoom.id = :roomId
              and member.leftAt is null
            """)
    List<ChatRoomMember> findAllActiveByRoomId(@Param("roomId") Long roomId);

    @Query("""
            select member.user.uuid from ChatRoomMember member
            where member.chatRoom.id = :roomId
              and member.user.uuid <> :senderUuid
              and member.leftAt is null
              and member.notificationEnabled = true
            """)
    List<UUID> findPushRecipientUuids(
            @Param("roomId") Long roomId, @Param("senderUuid") UUID senderUuid);
}
