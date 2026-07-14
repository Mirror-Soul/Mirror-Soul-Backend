package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MeetingRequestStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.match.MeetingReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.match.MeetingResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeetingServiceTest {
    private MeetingRequestRepository meetingRequestRepository;
    private ChatRoomRepository chatRoomRepository;
    private ChatRoomMemberRepository chatRoomMemberRepository;
    private UserRepository userRepository;
    private VideoCallRepository videoCallRepository;
    private MeetingService meetingService;

    @BeforeEach
    void setUp() {
        meetingRequestRepository = mock(MeetingRequestRepository.class);
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatRoomMemberRepository = mock(ChatRoomMemberRepository.class);
        userRepository = mock(UserRepository.class);
        videoCallRepository = mock(VideoCallRepository.class);
        meetingService = new MeetingService(
                meetingRequestRepository,
                mock(CallMatchAnalysisRepository.class),
                chatRoomRepository,
                chatRoomMemberRepository,
                userRepository,
                videoCallRepository
        );
    }

    @Test
    void createRequestStoresPendingRequestWithoutCreatingChatRoom() {
        UUID senderUuid = UUID.randomUUID();
        UUID receiverUuid = UUID.randomUUID();
        User sender = user(1L, senderUuid, UserStatus.ACTIVE);
        User receiver = user(2L, receiverUuid, UserStatus.ACTIVE);
        VideoCall call = completedCall(10L, sender, receiver);

        when(userRepository.findByUuid(senderUuid)).thenReturn(Optional.of(sender));
        when(userRepository.findByUuid(receiverUuid)).thenReturn(Optional.of(receiver));
        when(videoCallRepository.findById(10L)).thenReturn(Optional.of(call));
        when(meetingRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MeetingResDTO.CreatedDTO result = meetingService.createRequest(
                senderUuid, new MeetingReqDTO.CreateDTO(receiverUuid, 10L, " 만나고 싶어요 "));

        assertThat(result.status()).isEqualTo(MeetingRequestStatus.PENDING);
        verify(meetingRequestRepository).saveAndFlush(argThat(request ->
                request.getMessage().equals("만나고 싶어요") && request.getActivePairKey().equals("1:2")));
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void createRequestRejectsCallThatDoesNotBelongToSenderAndReceiver() {
        UUID senderUuid = UUID.randomUUID();
        UUID receiverUuid = UUID.randomUUID();
        User sender = user(1L, senderUuid, UserStatus.ACTIVE);
        User receiver = user(2L, receiverUuid, UserStatus.ACTIVE);
        User anotherUser = user(3L, UUID.randomUUID(), UserStatus.ACTIVE);
        VideoCall call = completedCall(10L, anotherUser, receiver);

        when(userRepository.findByUuid(senderUuid)).thenReturn(Optional.of(sender));
        when(userRepository.findByUuid(receiverUuid)).thenReturn(Optional.of(receiver));
        when(videoCallRepository.findById(10L)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> meetingService.createRequest(
                senderUuid, new MeetingReqDTO.CreateDTO(receiverUuid, 10L, "메시지")))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.MEETING_INVALID_CALL));
    }

    @Test
    void rejectChangesStatusAndDoesNotCreateChatRoom() {
        UUID receiverUuid = UUID.randomUUID();
        MeetingRequest request = MeetingRequest.builder()
                .sender(user(1L, UUID.randomUUID(), UserStatus.ACTIVE))
                .receiver(user(2L, receiverUuid, UserStatus.ACTIVE))
                .videoCall(mock(VideoCall.class))
                .message("메시지")
                .status(MeetingRequestStatus.PENDING)
                .activePairKey("1:2")
                .build();
        when(meetingRequestRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(request));

        MeetingResDTO.RespondedDTO result = meetingService.rejectRequest(receiverUuid, 100L);

        assertThat(result.status()).isEqualTo(MeetingRequestStatus.REJECTED);
        assertThat(request.getActivePairKey()).isNull();
        verifyNoInteractions(chatRoomMemberRepository);
    }

    @Test
    void acceptCreatesOneRoomAndTwoMembers() {
        UUID receiverUuid = UUID.randomUUID();
        MeetingRequest request = MeetingRequest.builder()
                .sender(user(1L, UUID.randomUUID(), UserStatus.ACTIVE))
                .receiver(user(2L, receiverUuid, UserStatus.ACTIVE))
                .videoCall(mock(VideoCall.class))
                .message("메시지")
                .status(MeetingRequestStatus.PENDING)
                .activePairKey("1:2")
                .build();
        ChatRoom savedRoom = mock(ChatRoom.class);
        when(savedRoom.getId()).thenReturn(500L);
        when(meetingRequestRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(request));
        when(chatRoomRepository.findByCreatedFromMeetingRequestId(100L)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any())).thenReturn(savedRoom);

        MeetingResDTO.AcceptedDTO result = meetingService.acceptRequest(receiverUuid, 100L);

        assertThat(result.status()).isEqualTo(MeetingRequestStatus.ACCEPTED);
        assertThat(result.chatRoomId()).isEqualTo(500L);
        assertThat(result.chatRoomCreated()).isTrue();
        verify(chatRoomMemberRepository).saveAll(argThat(members -> members.spliterator().getExactSizeIfKnown() == 2));
    }

    private User user(Long id, UUID uuid, UserStatus status) {
        return User.builder()
                .id(id)
                .uuid(uuid)
                .email(uuid + "@example.com")
                .passwordHash("password")
                .status(status)
                .build();
    }

    private VideoCall completedCall(Long id, User caller, User cloneOwner) {
        VideoCall call = mock(VideoCall.class);
        Clone clone = mock(Clone.class);
        when(call.getId()).thenReturn(id);
        when(call.getStatus()).thenReturn(VideoCallStatus.COMPLETED);
        when(call.getUser()).thenReturn(caller);
        when(call.getClone()).thenReturn(clone);
        when(clone.getUser()).thenReturn(cloneOwner);
        return call;
    }
}
