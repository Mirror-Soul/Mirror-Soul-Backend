package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.match.MatchResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

    private VideoCallRepository videoCallRepository;
    private MatchService matchService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        videoCallRepository = mock(VideoCallRepository.class);
        userRepository = mock(UserRepository.class);
        matchService = new MatchService(videoCallRepository, userRepository);
    }

    @Test
    void matchingCanBeDisabledAndReenabledAndRepeatedRequestsKeepTheRequestedState() {
        UUID uuid = UUID.randomUUID();
        User user = User.builder().uuid(uuid).matchingEnabled(true).build();
        when(userRepository.findByUuid(uuid)).thenReturn(Optional.of(user));

        assertThat(matchService.getMatchingStatus(uuid).matchingEnabled()).isTrue();
        assertThat(matchService.updateMatchingStatus(uuid, false).matchingEnabled()).isFalse();
        assertThat(user.getMatchingEnabled()).isFalse();
        assertThat(matchService.updateMatchingStatus(uuid, false).matchingEnabled()).isFalse();
        assertThat(matchService.getMatchingStatus(uuid).matchingEnabled()).isFalse();
        assertThat(matchService.updateMatchingStatus(uuid, true).matchingEnabled()).isTrue();
        assertThat(user.getMatchingEnabled()).isTrue();
    }

    @Test
    void matchingStatusRejectsUnknownUser() {
        UUID uuid = UUID.randomUUID();
        when(userRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getMatchingStatus(uuid)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> matchService.updateMatchingStatus(uuid, false)).isInstanceOf(GeneralException.class);
    }

    @Test
    void getTwinsReturnsDistinctTwinsWithLatestCallAndTotalCallCount() {
        UUID currentUserUuid = UUID.randomUUID();
        UUID firstTwinUserUuid = UUID.randomUUID();
        UUID secondTwinUserUuid = UUID.randomUUID();

        User firstTwinOwner = mock(User.class);
        when(firstTwinOwner.getUuid()).thenReturn(firstTwinUserUuid);
        when(firstTwinOwner.getName()).thenReturn("Jessica");
        when(firstTwinOwner.getProfileImageUrl()).thenReturn("https://example.com/jessica.png");

        User secondTwinOwner = mock(User.class);
        when(secondTwinOwner.getUuid()).thenReturn(secondTwinUserUuid);
        when(secondTwinOwner.getName()).thenReturn("Sarah");

        Clone firstTwin = mock(Clone.class);
        when(firstTwin.getUser()).thenReturn(firstTwinOwner);
        when(firstTwin.getAvatarImageUrl()).thenReturn("https://example.com/jessica-twin.png");
        when(firstTwin.getSyncRate()).thenReturn(90);
        when(firstTwin.getSummary()).thenReturn("여행과 사진 이야기를 좋아하는 Twin");

        Clone secondTwin = mock(Clone.class);
        when(secondTwin.getUser()).thenReturn(secondTwinOwner);

        LocalDateTime firstTwinLastCalledAt = LocalDateTime.of(2026, 7, 12, 15, 30);
        VideoCall firstTwinLatestCall = mockCall(
                30L,
                firstTwin,
                firstTwinLastCalledAt,
                CallMediaType.VOICE
        );
        VideoCall secondTwinLatestCall = mockCall(
                20L,
                secondTwin,
                LocalDateTime.of(2026, 7, 10, 10, 0),
                CallMediaType.VOICE
        );
        VideoCall firstTwinOlderCall = mockCall(
                10L,
                firstTwin,
                LocalDateTime.of(2026, 7, 1, 12, 0),
                CallMediaType.VOICE
        );

        when(videoCallRepository.findAllByUserUuidAndStatusOrderByLatest(
                currentUserUuid,
                VideoCallStatus.COMPLETED
        )).thenReturn(List.of(firstTwinLatestCall, secondTwinLatestCall, firstTwinOlderCall));

        MatchResDTO.TwinListDTO result = matchService.getTwins(currentUserUuid);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.twins()).extracting(MatchResDTO.TwinDTO::cloneUserUuid)
                .containsExactly(firstTwinUserUuid, secondTwinUserUuid);

        MatchResDTO.TwinDTO firstTwinResult = result.twins().get(0);
        assertThat(firstTwinResult.name()).isEqualTo("Jessica");
        assertThat(firstTwinResult.latestCallId()).isEqualTo(30L);
        assertThat(firstTwinResult.lastCalledAt()).isEqualTo(firstTwinLastCalledAt);
        assertThat(firstTwinResult.totalCallCount()).isEqualTo(2);
        assertThat(firstTwinResult.twinSyncRate()).isEqualTo(90);

        verify(videoCallRepository).findAllByUserUuidAndStatusOrderByLatest(
                currentUserUuid,
                VideoCallStatus.COMPLETED
        );
    }

    @Test
    void getTwinsReturnsEmptyListWhenThereIsNoCompletedCall() {
        UUID currentUserUuid = UUID.randomUUID();
        when(videoCallRepository.findAllByUserUuidAndStatusOrderByLatest(
                currentUserUuid,
                VideoCallStatus.COMPLETED
        )).thenReturn(List.of());

        MatchResDTO.TwinListDTO result = matchService.getTwins(currentUserUuid);

        assertThat(result.totalCount()).isZero();
        assertThat(result.twins()).isEmpty();
    }

    private VideoCall mockCall(
            Long id,
            Clone twin,
            LocalDateTime endedAt,
            CallMediaType mediaType
    ) {
        VideoCall call = mock(VideoCall.class);
        when(call.getId()).thenReturn(id);
        when(call.getClone()).thenReturn(twin);
        when(call.getEndedAt()).thenReturn(endedAt);
        when(call.getMediaType()).thenReturn(mediaType);
        return call;
    }
}
