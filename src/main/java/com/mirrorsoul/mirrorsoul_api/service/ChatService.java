package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.CallMatchAnalysis;
import com.mirrorsoul.mirrorsoul_api.domain.ChatMessage;
import com.mirrorsoul.mirrorsoul_api.domain.ChatRoom;
import com.mirrorsoul.mirrorsoul_api.domain.ChatRoomMember;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMatchAnalysisStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ChatMessageType;
import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatWebSocketEventDTO;
import com.mirrorsoul.mirrorsoul_api.event.ChatRealtimeEvent;
import com.mirrorsoul.mirrorsoul_api.event.ChatPushRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.CallMatchAnalysisRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ChatMessageRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ChatRoomMemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CallMatchAnalysisRepository callMatchAnalysisRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ChatResDTO.RoomListDTO getRooms(UUID currentUserUuid) {
        List<ChatRoomMember> myMemberships = chatRoomMemberRepository.findAllActiveByUserUuid(currentUserUuid);
        if (myMemberships.isEmpty()) {
            return ChatResDTO.RoomListDTO.builder().totalCount(0).rooms(List.of()).build();
        }

        List<Long> roomIds = myMemberships.stream().map(member -> member.getChatRoom().getId()).toList();
        Map<Long, User> partnersByRoomId = chatRoomMemberRepository.findAllActiveByRoomIdIn(roomIds).stream()
                .filter(member -> !member.getUser().getUuid().equals(currentUserUuid))
                .collect(Collectors.toMap(
                        member -> member.getChatRoom().getId(),
                        ChatRoomMember::getUser,
                        (first, ignored) -> first
                ));

        List<Long> lastMessageIds = myMemberships.stream()
                .map(member -> member.getChatRoom().getLastMessageId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ChatMessage> messagesById = lastMessageIds.isEmpty()
                ? Map.of()
                : chatMessageRepository.findAllWithSenderByIdIn(lastMessageIds).stream()
                        .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        Map<Long, Integer> similaritiesByCallId = loadSimilarities(myMemberships);
        List<ChatResDTO.RoomDTO> rooms = myMemberships.stream()
                .map(member -> toRoomDTO(
                        member,
                        partnersByRoomId.get(member.getChatRoom().getId()),
                        messagesById.get(member.getChatRoom().getLastMessageId()),
                        similaritiesByCallId,
                        currentUserUuid
                ))
                .filter(Objects::nonNull)
                .toList();

        return ChatResDTO.RoomListDTO.builder().totalCount(rooms.size()).rooms(rooms).build();
    }

    public ChatResDTO.MessageListDTO getMessages(
            UUID currentUserUuid, Long roomId, Long beforeMessageId, int size) {
        requireActiveMember(roomId, currentUserUuid);

        int fetchSize = Math.min(Math.max(size, 1), 100);
        List<ChatMessage> fetched = chatMessageRepository.findMessagesBefore(
                roomId, beforeMessageId, PageRequest.of(0, fetchSize + 1));
        boolean hasNext = fetched.size() > fetchSize;
        List<ChatMessage> page = new ArrayList<>(fetched.subList(0, Math.min(fetchSize, fetched.size())));
        Collections.reverse(page);

        Long nextCursor = hasNext && !page.isEmpty() ? page.get(0).getId() : null;
        return ChatResDTO.MessageListDTO.builder()
                .messages(page.stream().map(this::toMessageDTO).toList())
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    public ChatResDTO.NotificationSettingDTO getNotificationSetting(
            UUID currentUserUuid, Long roomId) {
        return toNotificationSettingDTO(requireActiveMember(roomId, currentUserUuid));
    }

    @Transactional
    public ChatResDTO.NotificationSettingDTO updateNotificationSetting(
            UUID currentUserUuid, Long roomId, ChatReqDTO.UpdateNotificationDTO request) {
        ChatRoomMember member = requireActiveMemberForUpdate(roomId, currentUserUuid);
        member.updateNotificationEnabled(request.enabled());
        return toNotificationSettingDTO(member);
    }

    @Transactional
    public ChatResDTO.MessageDTO sendMessage(
            UUID currentUserUuid, Long roomId, ChatReqDTO.SendMessageDTO request) {
        ChatRoomMember senderMember = requireActiveMemberForUpdate(roomId, currentUserUuid);

        Optional<ChatMessage> duplicate = chatMessageRepository
                .findByChatRoomIdAndSenderUuidAndClientMessageId(
                        roomId, currentUserUuid, request.clientMessageId());
        if (duplicate.isPresent()) {
            return toMessageDTO(duplicate.get());
        }

        ChatMessage message = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .chatRoom(senderMember.getChatRoom())
                .sender(senderMember.getUser())
                .clientMessageId(request.clientMessageId())
                .messageType(ChatMessageType.TEXT)
                .content(request.content().trim())
                .build());
        senderMember.getChatRoom().updateLastMessage(message);

        ChatResDTO.MessageDTO result = toMessageDTO(message);
        publishToRoom(roomId, new ChatWebSocketEventDTO(
                "MESSAGE_CREATED", roomId, message.getCreatedAt(), result));
        eventPublisher.publishEvent(new ChatPushRequestedEvent(
                roomId,
                message.getId(),
                senderMember.getUser().getUuid(),
                senderMember.getUser().getName()
        ));
        return result;
    }

    @Transactional
    public ChatResDTO.ReadResultDTO readMessages(
            UUID currentUserUuid, Long roomId, ChatReqDTO.ReadMessageDTO request) {
        ChatRoomMember member = requireActiveMemberForUpdate(roomId, currentUserUuid);
        ChatMessage message = chatMessageRepository.findById(request.lastReadMessageId())
                .filter(found -> found.getChatRoom().getId().equals(roomId))
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CHAT_MESSAGE_NOT_FOUND));

        LocalDateTime readAt = LocalDateTime.now();
        boolean updated = member.readThrough(message.getId(), readAt);
        ChatResDTO.ReadResultDTO result = ChatResDTO.ReadResultDTO.builder()
                .chatRoomId(roomId)
                .lastReadMessageId(member.getLastReadMessageId())
                .lastReadAt(member.getLastReadAt())
                .updated(updated)
                .build();

        if (updated) {
            publishToRoom(roomId, new ChatWebSocketEventDTO(
                    "MESSAGE_READ",
                    roomId,
                    readAt,
                    new ChatWebSocketEventDTO.MessageReadData(currentUserUuid, message.getId(), readAt)
            ));
        }
        return result;
    }

    private ChatRoomMember requireActiveMember(Long roomId, UUID userUuid) {
        return chatRoomMemberRepository.findActiveByRoomIdAndUserUuid(roomId, userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    private ChatRoomMember requireActiveMemberForUpdate(Long roomId, UUID userUuid) {
        return chatRoomMemberRepository.findActiveByRoomIdAndUserUuidForUpdate(roomId, userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    private void publishToRoom(Long roomId, ChatWebSocketEventDTO payload) {
        Set<UUID> recipients = chatRoomMemberRepository.findAllActiveByRoomId(roomId).stream()
                .map(member -> member.getUser().getUuid())
                .collect(Collectors.toSet());
        eventPublisher.publishEvent(new ChatRealtimeEvent(recipients, payload));
    }

    private Map<Long, Integer> loadSimilarities(List<ChatRoomMember> memberships) {
        List<Long> callIds = memberships.stream()
                .map(member -> member.getChatRoom().getCreatedFromMeetingRequest().getVideoCall().getId())
                .distinct()
                .toList();
        return callMatchAnalysisRepository.findAllByVideoCallIdIn(callIds).stream()
                .filter(analysis -> analysis.getStatus() == CallMatchAnalysisStatus.COMPLETED)
                .collect(Collectors.toMap(
                        analysis -> analysis.getVideoCall().getId(),
                        CallMatchAnalysis::getTwinSimilarity
                ));
    }

    private ChatResDTO.RoomDTO toRoomDTO(
            ChatRoomMember membership,
            User partner,
            ChatMessage lastMessage,
            Map<Long, Integer> similaritiesByCallId,
            UUID currentUserUuid) {
        if (partner == null) {
            return null;
        }
        ChatRoom room = membership.getChatRoom();
        long unreadCount = chatMessageRepository.countByChatRoomIdAndSenderUuidNotAndIdGreaterThan(
                room.getId(), currentUserUuid,
                membership.getLastReadMessageId() == null ? 0L : membership.getLastReadMessageId());
        Long callId = room.getCreatedFromMeetingRequest().getVideoCall().getId();

        return ChatResDTO.RoomDTO.builder()
                .chatRoomId(room.getId())
                .partner(ChatResDTO.PartnerDTO.builder()
                        .userUuid(partner.getUuid())
                        .name(partner.getName())
                        .profileImageUrl(partner.getProfileImageUrl())
                        .age(calculateAge(partner.getBirthDate()))
                        .twinSimilarity(similaritiesByCallId.get(callId))
                        .lastActiveAt(partner.getLastActiveAt())
                        .build())
                .lastMessage(lastMessage == null ? null : toMessageDTO(lastMessage))
                .unreadCount(unreadCount)
                .notificationEnabled(Boolean.TRUE.equals(membership.getNotificationEnabled()))
                .createdAt(room.getCreatedAt())
                .build();
    }

    private ChatResDTO.NotificationSettingDTO toNotificationSettingDTO(ChatRoomMember member) {
        return ChatResDTO.NotificationSettingDTO.builder()
                .chatRoomId(member.getChatRoom().getId())
                .enabled(Boolean.TRUE.equals(member.getNotificationEnabled()))
                .build();
    }

    private ChatResDTO.MessageDTO toMessageDTO(ChatMessage message) {
        return ChatResDTO.MessageDTO.builder()
                .messageId(message.getId())
                .chatRoomId(message.getChatRoom().getId())
                .senderUserUuid(message.getSender().getUuid())
                .clientMessageId(message.getClientMessageId())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private Integer calculateAge(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}
