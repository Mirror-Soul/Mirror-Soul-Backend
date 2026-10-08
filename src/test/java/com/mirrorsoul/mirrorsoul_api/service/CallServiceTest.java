package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserBlockRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkTimeTransactionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CallServiceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(CallMediaType.class)
    void disabledMatchingRejectsOtherUsersCalls(CallMediaType mediaType) {
        UUID callerUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        User caller = mock(User.class);
        User owner = User.builder().id(2L).uuid(ownerUuid)
                .status(com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus.ACTIVE)
                .matchingEnabled(true).build();
        Clone clone = mock(Clone.class);
        when(caller.getId()).thenReturn(1L);
        when(caller.hasTalkTime()).thenReturn(true);
        when(clone.getUser()).thenReturn(owner);
        when(userRepository.findByUuid(callerUuid)).thenReturn(Optional.of(caller));
        when(userRepository.findByUuid(ownerUuid)).thenReturn(Optional.of(owner));
        when(cloneRepository.findByUserUuid(ownerUuid)).thenReturn(Optional.of(clone));

        new MatchService(videoCallRepository, userRepository, mock(ProfileImageUrlService.class))
                .updateMatchingStatus(ownerUuid, false);

        assertThatThrownBy(() -> callService.startCloneCall(
                ownerUuid, new CallReqDTO.StartCallDTO(mediaType), callerUuid))
                .isInstanceOf(com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException.class);
        verify(videoCallRepository, never()).save(any(VideoCall.class));
    }

    private VideoCallRepository videoCallRepository;
    private UserRepository userRepository;
    private CloneRepository cloneRepository;
    private UserBlockRepository userBlockRepository;
    private TalkTimeTransactionRepository talkTimeTransactionRepository;
    private CallService callService;

    @BeforeEach
    void setUp() {
        videoCallRepository = mock(VideoCallRepository.class);
        userRepository = mock(UserRepository.class);
        cloneRepository = mock(CloneRepository.class);
        userBlockRepository = mock(UserBlockRepository.class);
        talkTimeTransactionRepository = mock(TalkTimeTransactionRepository.class);
        callService = new CallService(
                videoCallRepository,
                userRepository,
                cloneRepository,
                userBlockRepository,
                talkTimeTransactionRepository
        );
    }

    @Test
    void startCloneCallAllowsCallingOwnClone() {
        UUID userUuid = UUID.randomUUID();
        User caller = mock(User.class);
        Clone ownClone = mock(Clone.class);
        when(caller.getId()).thenReturn(1L);
        when(caller.hasTalkTime()).thenReturn(true);
        when(ownClone.getUser()).thenReturn(caller);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(caller));
        when(cloneRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(ownClone));
        when(videoCallRepository.save(any(VideoCall.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CallResDTO.StartCallDTO result = callService.startCloneCall(
                userUuid,
                new CallReqDTO.StartCallDTO(CallMediaType.VOICE),
                userUuid
        );

        assertThat(result.roomId()).startsWith("call-");
        assertThat(result.mediaType()).isEqualTo(CallMediaType.VOICE);
        verify(videoCallRepository).save(any(VideoCall.class));
        verify(userBlockRepository, never()).existsBetween(any(), any());
    }

    @Test
    void startCloneCallRejectsOtherUsersPendingClone() {
        UUID callerUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        User caller = mock(User.class);
        User owner = User.builder().id(2L).uuid(ownerUuid)
                .status(com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus.ACTIVE)
                .matchingEnabled(true).build();
        Clone clone = mock(Clone.class);
        when(caller.getId()).thenReturn(1L);
        when(caller.hasTalkTime()).thenReturn(true);
        when(clone.getUser()).thenReturn(owner);
        when(clone.getStatus()).thenReturn("PENDING");
        when(userRepository.findByUuid(callerUuid)).thenReturn(Optional.of(caller));
        when(cloneRepository.findByUserUuid(ownerUuid)).thenReturn(Optional.of(clone));

        assertThatThrownBy(() -> callService.startCloneCall(
                ownerUuid,
                new CallReqDTO.StartCallDTO(CallMediaType.VOICE),
                callerUuid
        ))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.CLONE_NOT_READY));
        verify(videoCallRepository, never()).save(any(VideoCall.class));
    }

    @Test
    void endCallRecordsActualDeductionWhenBalanceReachesZero() {
        UUID userUuid = UUID.randomUUID();
        User caller = User.builder().id(1L).uuid(userUuid).remainingTalkTime(60).build();
        Clone clone = Clone.builder().user(caller).build();
        VideoCall call = VideoCall.builder().user(caller).clone(clone)
                .roomId("call-test").mediaType(CallMediaType.VOICE).build();
        ReflectionTestUtils.setField(call, "startedAt", LocalDateTime.now().minusSeconds(120));
        when(videoCallRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(call));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(caller));

        CallResDTO.EndCallDTO result = callService.endCall(10L, null, userUuid);

        assertThat(result.remainingTalkTime()).isZero();
        ArgumentCaptor<TalkTimeTransaction> transactionCaptor =
                ArgumentCaptor.forClass(TalkTimeTransaction.class);
        verify(talkTimeTransactionRepository).save(transactionCaptor.capture());
        TalkTimeTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getReason()).isEqualTo(TalkTimeTransactionReason.CALL_USAGE);
        assertThat(transaction.getVideoCall()).isSameAs(call);
        assertThat(transaction.getDeltaSeconds()).isEqualTo(-60);
        assertThat(transaction.getBalanceAfterSeconds()).isZero();
    }
}
