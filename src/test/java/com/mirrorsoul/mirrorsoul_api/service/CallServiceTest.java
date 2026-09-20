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
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserBlockRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        new MatchService(videoCallRepository, userRepository).updateMatchingStatus(ownerUuid, false);

        assertThatThrownBy(() -> callService.startCloneCall(
                ownerUuid, new CallReqDTO.StartCallDTO(mediaType), callerUuid))
                .isInstanceOf(com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException.class);
        verify(videoCallRepository, never()).save(any(VideoCall.class));
    }

    private VideoCallRepository videoCallRepository;
    private UserRepository userRepository;
    private CloneRepository cloneRepository;
    private UserBlockRepository userBlockRepository;
    private CallService callService;

    @BeforeEach
    void setUp() {
        videoCallRepository = mock(VideoCallRepository.class);
        userRepository = mock(UserRepository.class);
        cloneRepository = mock(CloneRepository.class);
        userBlockRepository = mock(UserBlockRepository.class);
        callService = new CallService(
                videoCallRepository,
                userRepository,
                cloneRepository,
                userBlockRepository
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
}
