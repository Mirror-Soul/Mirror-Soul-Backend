package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.TalkLog;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Speaker;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiTalkLogReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiTalkLogResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.TalkLogRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiTalkLogServiceTest {

    @Mock
    private VideoCallRepository videoCallRepository;

    @Mock
    private TalkLogRepository talkLogRepository;

    private AiTalkLogService service;

    @BeforeEach
    void setUp() {
        service = new AiTalkLogService(videoCallRepository, talkLogRepository);
    }

    @Test
    void savesFinalizedUtteranceAndNormalizesTimeToSeoul() {
        Long callId = 1L;
        UUID eventId = UUID.randomUUID();
        VideoCall call = inProgressCall(callId);
        AiTalkLogReqDTO.Save request = request(
                eventId,
                OffsetDateTime.parse("2026-10-03T05:30:10Z"),
                OffsetDateTime.parse("2026-10-03T05:30:12Z")
        );

        when(videoCallRepository.findByIdForUpdate(callId)).thenReturn(Optional.of(call));
        when(talkLogRepository.findByVideoCallIdAndEventId(callId, eventId.toString()))
                .thenReturn(Optional.empty());
        when(talkLogRepository.save(any(TalkLog.class))).thenAnswer(invocation -> {
            TalkLog saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 15L);
            return saved;
        });

        AiTalkLogResDTO.Saved result = service.save(callId, request);

        assertThat(result.talkLogId()).isEqualTo(15L);
        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.duplicated()).isFalse();

        ArgumentCaptor<TalkLog> captor = ArgumentCaptor.forClass(TalkLog.class);
        verify(talkLogRepository).save(captor.capture());
        TalkLog saved = captor.getValue();
        assertThat(saved.getVideoCall()).isSameAs(call);
        assertThat(saved.getEventId()).isEqualTo(eventId.toString());
        assertThat(saved.getSpeaker()).isEqualTo(Speaker.USER);
        assertThat(saved.getMessage()).isEqualTo("안녕, 오늘 뭐 했어?");
        assertThat(saved.getStartedAt()).isEqualTo(LocalDateTime.parse("2026-10-03T14:30:10"));
        assertThat(saved.getEndedAt()).isEqualTo(LocalDateTime.parse("2026-10-03T14:30:12"));
        assertThat(saved.isEdited()).isFalse();
    }

    @Test
    void returnsExistingTalkLogForSameCallAndEvent() {
        Long callId = 1L;
        UUID eventId = UUID.randomUUID();
        VideoCall call = inProgressCall(callId);
        TalkLog existing = TalkLog.builder()
                .id(15L)
                .videoCall(call)
                .eventId(eventId.toString())
                .speaker(Speaker.USER)
                .message("기존 발화")
                .startedAt(LocalDateTime.now())
                .edited(false)
                .build();

        when(videoCallRepository.findByIdForUpdate(callId)).thenReturn(Optional.of(call));
        when(talkLogRepository.findByVideoCallIdAndEventId(callId, eventId.toString()))
                .thenReturn(Optional.of(existing));

        AiTalkLogResDTO.Saved result = service.save(callId, request(
                eventId,
                OffsetDateTime.parse("2026-10-03T14:30:10+09:00"),
                null
        ));

        assertThat(result.talkLogId()).isEqualTo(15L);
        assertThat(result.duplicated()).isTrue();
        verify(talkLogRepository, never()).save(any());
    }

    @Test
    void allowsSaveAfterCallCompleted() {
        Long callId = 1L;
        UUID eventId = UUID.randomUUID();
        VideoCall call = inProgressCall(callId);
        call.complete();

        when(videoCallRepository.findByIdForUpdate(callId)).thenReturn(Optional.of(call));
        when(talkLogRepository.findByVideoCallIdAndEventId(callId, eventId.toString()))
                .thenReturn(Optional.empty());
        when(talkLogRepository.save(any(TalkLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiTalkLogResDTO.Saved result = service.save(callId, request(
                eventId,
                OffsetDateTime.parse("2026-10-03T14:30:10+09:00"),
                null
        ));

        assertThat(result.duplicated()).isFalse();
        verify(talkLogRepository).save(any(TalkLog.class));
    }

    @Test
    void rejectsCallThatHasNotStarted() {
        Long callId = 1L;
        UUID eventId = UUID.randomUUID();
        VideoCall readyCall = VideoCall.builder()
                .roomId("call-abc")
                .mediaType(CallMediaType.VIDEO)
                .build();

        when(videoCallRepository.findByIdForUpdate(callId)).thenReturn(Optional.of(readyCall));
        when(talkLogRepository.findByVideoCallIdAndEventId(callId, eventId.toString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(callId, request(
                eventId,
                OffsetDateTime.parse("2026-10-03T14:30:10+09:00"),
                null
        )))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getCode())
                .isEqualTo(GeneralErrorCode.TALK_LOG_INVALID_CALL_STATUS);

        verify(talkLogRepository, never()).save(any());
    }

    @Test
    void rejectsEndedAtBeforeStartedAt() {
        Long callId = 1L;
        UUID eventId = UUID.randomUUID();
        VideoCall call = inProgressCall(callId);

        when(videoCallRepository.findByIdForUpdate(callId)).thenReturn(Optional.of(call));
        when(talkLogRepository.findByVideoCallIdAndEventId(callId, eventId.toString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(callId, request(
                eventId,
                OffsetDateTime.parse("2026-10-03T14:30:12+09:00"),
                OffsetDateTime.parse("2026-10-03T14:30:10+09:00")
        )))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getCode())
                .isEqualTo(GeneralErrorCode.TALK_LOG_INVALID_TIME_RANGE);

        verify(talkLogRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownCall() {
        when(videoCallRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(404L, request(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-10-03T14:30:10+09:00"),
                null
        )))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getCode())
                .isEqualTo(GeneralErrorCode.CALL_NOT_FOUND);
    }

    private VideoCall inProgressCall(Long id) {
        VideoCall call = VideoCall.builder()
                .roomId("call-abc")
                .mediaType(CallMediaType.VIDEO)
                .build();
        ReflectionTestUtils.setField(call, "id", id);
        call.start();
        return call;
    }

    private AiTalkLogReqDTO.Save request(
            UUID eventId,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt
    ) {
        return new AiTalkLogReqDTO.Save(
                eventId,
                Speaker.USER,
                "  안녕, 오늘 뭐 했어?  ",
                startedAt,
                endedAt
        );
    }
}
