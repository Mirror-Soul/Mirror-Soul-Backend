package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.ChatMessage;
import com.mirrorsoul.mirrorsoul_api.domain.ChatRoom;
import com.mirrorsoul.mirrorsoul_api.domain.ChatRoomMember;
import com.mirrorsoul.mirrorsoul_api.domain.MeetingRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ChatMessageType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ChatRoomType;
import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatResDTO;
import com.mirrorsoul.mirrorsoul_api.event.ChatRealtimeEvent;
import com.mirrorsoul.mirrorsoul_api.repository.CallMatchAnalysisRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ChatMessageRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ChatRoomMemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

class ChatServiceTest {
    private ChatRoomMemberRepository chatRoomMemberRepository;
    private ChatMessageRepository chatMessageRepository;
    private CallMatchAnalysisRepository callMatchAnalysisRepository;
    private ApplicationEventPublisher eventPublisher;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatRoomMemberRepository = mock(ChatRoomMemberRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        callMatchAnalysisRepository = mock(CallMatchAnalysisRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        chatService = new ChatService(
                chatRoomMemberRepository,
                chatMessageRepository,
                callMatchAnalysisRepository,
                eventPublisher
        );
    }

    @Test
    void getRoomsReturnsPartnerLastMessageAndUnreadCount() {
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = user(1L, currentUserUuid);
        User partner = user(2L, UUID.randomUUID());
        VideoCall videoCall = mock(VideoCall.class);
        when(videoCall.getId()).thenReturn(30L);
        MeetingRequest meetingRequest = MeetingRequest.builder()
                .sender(currentUser)
                .receiver(partner)
                .videoCall(videoCall)
                .message("만남 신청")
                .build();
        ChatRoom room = ChatRoom.builder()
                .id(10L)
                .participantPairKey("1:2")
                .roomType(ChatRoomType.DIRECT)
                .createdFromMeetingRequest(meetingRequest)
                .build();
        ChatRoomMember myMembership = member(100L, room, currentUser);
        ChatRoomMember partnerMembership = member(101L, room, partner);
        ChatMessage lastMessage = message(
                500L, room, partner, UUID.randomUUID(), "마지막 메시지", LocalDateTime.now());
        room.updateLastMessage(lastMessage);

        when(chatRoomMemberRepository.findAllActiveByUserUuid(currentUserUuid))
                .thenReturn(List.of(myMembership));
        when(chatRoomMemberRepository.findAllActiveByRoomIdIn(List.of(10L)))
                .thenReturn(List.of(myMembership, partnerMembership));
        when(chatMessageRepository.findAllWithSenderByIdIn(List.of(500L)))
                .thenReturn(List.of(lastMessage));
        when(callMatchAnalysisRepository.findAllByVideoCallIdIn(List.of(30L)))
                .thenReturn(List.of());
        when(chatMessageRepository.countByChatRoomIdAndSenderUuidNotAndIdGreaterThan(
                10L, currentUserUuid, 0L)).thenReturn(2L);

        ChatResDTO.RoomListDTO result = chatService.getRooms(currentUserUuid);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.rooms().get(0).partner().userUuid()).isEqualTo(partner.getUuid());
        assertThat(result.rooms().get(0).lastMessage().content()).isEqualTo("마지막 메시지");
        assertThat(result.rooms().get(0).unreadCount()).isEqualTo(2L);
        assertThat(result.rooms().get(0).notificationEnabled()).isTrue();
    }

    @Test
    void getNotificationSettingReturnsCurrentUsersRoomSetting() {
        UUID userUuid = UUID.randomUUID();
        User user = user(1L, userUuid);
        ChatRoom room = room(10L);
        ChatRoomMember member = member(100L, room, user);
        member.updateNotificationEnabled(false);
        when(chatRoomMemberRepository.findActiveByRoomIdAndUserUuid(10L, userUuid))
                .thenReturn(Optional.of(member));

        ChatResDTO.NotificationSettingDTO result =
                chatService.getNotificationSetting(userUuid, 10L);

        assertThat(result.chatRoomId()).isEqualTo(10L);
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void updateNotificationSettingChangesCurrentMembership() {
        UUID userUuid = UUID.randomUUID();
        User user = user(1L, userUuid);
        ChatRoom room = room(10L);
        ChatRoomMember member = member(100L, room, user);
        when(chatRoomMemberRepository.findActiveByRoomIdAndUserUuidForUpdate(10L, userUuid))
                .thenReturn(Optional.of(member));

        ChatResDTO.NotificationSettingDTO result = chatService.updateNotificationSetting(
                userUuid, 10L, new ChatReqDTO.UpdateNotificationDTO(false));

        assertThat(result.enabled()).isFalse();
        assertThat(member.getNotificationEnabled()).isFalse();
    }

    @Test
    void sendMessagePersistsTrimmedTextAndPublishesRoomEvent() {
        UUID senderUuid = UUID.randomUUID();
        UUID receiverUuid = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        User sender = user(1L, senderUuid);
        User receiver = user(2L, receiverUuid);
        ChatRoom room = room(10L);
        ChatRoomMember senderMember = member(100L, room, sender);
        ChatRoomMember receiverMember = member(101L, room, receiver);
        LocalDateTime createdAt = LocalDateTime.now();

        when(chatRoomMemberRepository.findActiveByRoomIdAndUserUuidForUpdate(10L, senderUuid))
                .thenReturn(Optional.of(senderMember));
        when(chatMessageRepository.findByChatRoomIdAndSenderUuidAndClientMessageId(
                10L, senderUuid, clientMessageId)).thenReturn(Optional.empty());
        when(chatMessageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ChatMessage input = invocation.getArgument(0);
            return message(500L, room, sender, input.getClientMessageId(), input.getContent(), createdAt);
        });
        when(chatRoomMemberRepository.findAllActiveByRoomId(10L))
                .thenReturn(List.of(senderMember, receiverMember));

        ChatResDTO.MessageDTO result = chatService.sendMessage(
                senderUuid, 10L, new ChatReqDTO.SendMessageDTO(clientMessageId, "  안녕하세요  "));

        assertThat(result.messageId()).isEqualTo(500L);
        assertThat(result.content()).isEqualTo("안녕하세요");
        assertThat(room.getLastMessageId()).isEqualTo(500L);
        assertThat(room.getLastMessageAt()).isEqualTo(createdAt);
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<ChatRealtimeEvent>argThat(event ->
                event.recipients().containsAll(List.of(senderUuid, receiverUuid))
                        && event.payload().type().equals("MESSAGE_CREATED")));
    }

    @Test
    void repeatedClientMessageIdReturnsExistingMessageWithoutPublishingAgain() {
        UUID senderUuid = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        User sender = user(1L, senderUuid);
        ChatRoom room = room(10L);
        ChatRoomMember senderMember = member(100L, room, sender);
        ChatMessage existing = message(
                500L, room, sender, clientMessageId, "안녕하세요", LocalDateTime.now());

        when(chatRoomMemberRepository.findActiveByRoomIdAndUserUuidForUpdate(10L, senderUuid))
                .thenReturn(Optional.of(senderMember));
        when(chatMessageRepository.findByChatRoomIdAndSenderUuidAndClientMessageId(
                10L, senderUuid, clientMessageId)).thenReturn(Optional.of(existing));

        ChatResDTO.MessageDTO result = chatService.sendMessage(
                senderUuid, 10L, new ChatReqDTO.SendMessageDTO(clientMessageId, "안녕하세요"));

        assertThat(result.messageId()).isEqualTo(500L);
        verify(chatMessageRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getMessagesReturnsChronologicalPageAndOldestCursor() {
        UUID userUuid = UUID.randomUUID();
        User user = user(1L, userUuid);
        ChatRoom room = room(10L);
        ChatRoomMember member = member(100L, room, user);
        when(chatRoomMemberRepository.findActiveByRoomIdAndUserUuid(10L, userUuid))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository.findMessagesBefore(eq(10L), isNull(), any(Pageable.class)))
                .thenReturn(List.of(
                        message(3L, room, user, UUID.randomUUID(), "세 번째", LocalDateTime.now()),
                        message(2L, room, user, UUID.randomUUID(), "두 번째", LocalDateTime.now()),
                        message(1L, room, user, UUID.randomUUID(), "첫 번째", LocalDateTime.now())
                ));

        ChatResDTO.MessageListDTO result = chatService.getMessages(userUuid, 10L, null, 2);

        assertThat(result.messages()).extracting(ChatResDTO.MessageDTO::messageId)
                .containsExactly(2L, 3L);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(2L);
    }

    @Test
    void readMessagesOnlyMovesReadCursorForwardAndPublishesEvent() {
        UUID userUuid = UUID.randomUUID();
        User user = user(1L, userUuid);
        User receiver = user(2L, UUID.randomUUID());
        ChatRoom room = room(10L);
        ChatRoomMember member = member(100L, room, user);
        ChatRoomMember receiverMember = member(101L, room, receiver);
        ChatMessage message = message(
                50L, room, receiver, UUID.randomUUID(), "읽을 메시지", LocalDateTime.now());

        when(chatRoomMemberRepository.findActiveByRoomIdAndUserUuidForUpdate(10L, userUuid))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository.findById(50L)).thenReturn(Optional.of(message));
        when(chatRoomMemberRepository.findAllActiveByRoomId(10L))
                .thenReturn(List.of(member, receiverMember));

        ChatResDTO.ReadResultDTO first = chatService.readMessages(
                userUuid, 10L, new ChatReqDTO.ReadMessageDTO(50L));
        ChatResDTO.ReadResultDTO repeated = chatService.readMessages(
                userUuid, 10L, new ChatReqDTO.ReadMessageDTO(50L));

        assertThat(first.updated()).isTrue();
        assertThat(repeated.updated()).isFalse();
        assertThat(member.getLastReadMessageId()).isEqualTo(50L);
        verify(eventPublisher, times(1)).publishEvent(any(ChatRealtimeEvent.class));
    }

    @Test
    void nonMemberCannotReadMessages() {
        UUID userUuid = UUID.randomUUID();
        when(chatRoomMemberRepository.findActiveByRoomIdAndUserUuid(10L, userUuid))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(userUuid, 10L, null, 30))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    private User user(Long id, UUID uuid) {
        return User.builder()
                .id(id)
                .uuid(uuid)
                .email(uuid + "@example.com")
                .passwordHash("password")
                .build();
    }

    private ChatRoom room(Long id) {
        return ChatRoom.builder()
                .id(id)
                .participantPairKey("1:2")
                .roomType(ChatRoomType.DIRECT)
                .build();
    }

    private ChatRoomMember member(Long id, ChatRoom room, User user) {
        return ChatRoomMember.builder()
                .id(id)
                .chatRoom(room)
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    private ChatMessage message(
            Long id,
            ChatRoom room,
            User sender,
            UUID clientMessageId,
            String content,
            LocalDateTime createdAt) {
        return ChatMessage.builder()
                .id(id)
                .chatRoom(room)
                .sender(sender)
                .clientMessageId(clientMessageId)
                .messageType(ChatMessageType.TEXT)
                .content(content)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
