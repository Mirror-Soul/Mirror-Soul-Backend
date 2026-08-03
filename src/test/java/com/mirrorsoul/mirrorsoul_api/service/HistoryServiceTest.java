package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.CallMatchAnalysis;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.TalkLog;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMatchAnalysisStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Speaker;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CallMatchAnalysisRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkLogRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryServiceTest {

    private VideoCallRepository videoCallRepository;
    private CallMatchAnalysisRepository callMatchAnalysisRepository;
    private CloneRepository cloneRepository;
    private TalkLogRepository talkLogRepository;
    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        videoCallRepository = mock(VideoCallRepository.class);
        callMatchAnalysisRepository = mock(CallMatchAnalysisRepository.class);
        cloneRepository = mock(CloneRepository.class);
        talkLogRepository = mock(TalkLogRepository.class);
        historyService = new HistoryService(
                videoCallRepository,
                callMatchAnalysisRepository,
                cloneRepository,
                talkLogRepository
        );
    }

    @Test
    void getCallHistoryReturnsSummaryAndDateGroups() {
        LocalDate today = LocalDate.now();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = user(currentUserUuid, "나", today.minusYears(26));
        User sarah = user(UUID.randomUUID(), "Sarah", today.minusYears(28));
        User emily = user(UUID.randomUUID(), "Emily", today.minusYears(26));
        Clone myClone = clone(currentUser, 88);
        Clone sarahClone = clone(sarah, 94);
        Clone emilyClone = clone(emily, 89);

        VideoCall sent = call(
                11L, currentUser, sarahClone, today.atTime(14, 30), 503, CallMediaType.VIDEO);
        VideoCall received = call(
                10L, emily, myClone, today.minusDays(1).atTime(19, 20), 765, CallMediaType.VOICE);
        when(videoCallRepository.findRecentHistory(
                currentUserUuid,
                VideoCallStatus.COMPLETED,
                today.minusDays(6).atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(sent, received));

        CallMatchAnalysis analysis = analysis(sent, 92, List.of("커피", "음악", "주말 계획"));
        when(callMatchAnalysisRepository.findAllByVideoCallIdIn(List.of(11L, 10L)))
                .thenReturn(List.of(analysis));
        when(cloneRepository.findAllByUserUuidIn(List.of(sarah.getUuid(), emily.getUuid())))
                .thenReturn(List.of(sarahClone, emilyClone));

        HistoryResDTO.CallHistoryListDTO result = historyService.getCallHistory(
                currentUserUuid,
                HistoryReqDTO.HistoryType.ALL
        );

        assertThat(result.summary().totalCount()).isEqualTo(2);
        assertThat(result.summary().sentCount()).isEqualTo(1);
        assertThat(result.summary().receivedCount()).isEqualTo(1);
        assertThat(result.groups()).hasSize(2);
        assertThat(result.groups().get(0).date()).isEqualTo(today);
        assertThat(result.groups().get(1).date()).isEqualTo(today.minusDays(1));

        HistoryResDTO.CallHistoryDTO sentResult = result.groups().get(0).histories().get(0);
        assertThat(sentResult.type()).isEqualTo(HistoryReqDTO.HistoryType.SENT);
        assertThat(sentResult.partner().name()).isEqualTo("Sarah");
        assertThat(sentResult.partner().age()).isEqualTo(28);
        assertThat(sentResult.partner().twinSyncRate()).isEqualTo(94);
        assertThat(sentResult.matchTarget()).isEqualTo(HistoryResDTO.MatchTarget.PARTNER_TWIN);
        assertThat(sentResult.matchScore()).isEqualTo(92);
        assertThat(sentResult.topics()).containsExactly("커피", "음악", "주말 계획");
        assertThat(sentResult.isNew()).isTrue();

        HistoryResDTO.CallHistoryDTO receivedResult = result.groups().get(1).histories().get(0);
        assertThat(receivedResult.type()).isEqualTo(HistoryReqDTO.HistoryType.RECEIVED);
        assertThat(receivedResult.partner().name()).isEqualTo("Emily");
        assertThat(receivedResult.matchTarget()).isEqualTo(HistoryResDTO.MatchTarget.MY_TWIN);
        assertThat(receivedResult.matchScore()).isNull();
        assertThat(receivedResult.topics()).isEmpty();
        assertThat(receivedResult.isNew()).isFalse();
    }

    @Test
    void getCallHistoryFiltersListButKeepsWholeSummary() {
        LocalDate today = LocalDate.now();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = user(currentUserUuid, "나", today.minusYears(26));
        User sarah = user(UUID.randomUUID(), "Sarah", today.minusYears(28));
        User emily = user(UUID.randomUUID(), "Emily", today.minusYears(26));
        Clone myClone = clone(currentUser, 88);
        Clone sarahClone = clone(sarah, 94);
        Clone emilyClone = clone(emily, 89);
        VideoCall sent = call(
                11L, currentUser, sarahClone, today.atTime(14, 30), 503, CallMediaType.VIDEO);
        VideoCall received = call(
                10L, emily, myClone, today.atTime(11, 15), 765, CallMediaType.VOICE);

        when(videoCallRepository.findRecentHistory(
                currentUserUuid,
                VideoCallStatus.COMPLETED,
                today.minusDays(6).atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(sent, received));
        when(callMatchAnalysisRepository.findAllByVideoCallIdIn(List.of(10L)))
                .thenReturn(List.of());
        when(cloneRepository.findAllByUserUuidIn(List.of(emily.getUuid())))
                .thenReturn(List.of(emilyClone));

        HistoryResDTO.CallHistoryListDTO result = historyService.getCallHistory(
                currentUserUuid,
                HistoryReqDTO.HistoryType.RECEIVED
        );

        assertThat(result.summary().totalCount()).isEqualTo(2);
        assertThat(result.summary().sentCount()).isEqualTo(1);
        assertThat(result.summary().receivedCount()).isEqualTo(1);
        assertThat(result.groups()).singleElement()
                .satisfies(group -> assertThat(group.histories()).singleElement()
                        .satisfies(history -> assertThat(history.callId()).isEqualTo(10L)));
        verify(callMatchAnalysisRepository).findAllByVideoCallIdIn(List.of(10L));
    }

    @Test
    void getWeeklySummaryComparesSameElapsedPeriodFromPreviousWeek() {
        LocalDate today = LocalDate.now();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = user(currentUserUuid, "나", today.minusYears(26));
        User sarah = user(UUID.randomUUID(), "Sarah", today.minusYears(28));
        User emily = user(UUID.randomUUID(), "Emily", today.minusYears(26));
        Clone myClone = clone(currentUser, 88);
        Clone sarahClone = clone(sarah, 94);
        VideoCall sent = call(
                31L, currentUser, sarahClone, today.atTime(10, 0), 3600, CallMediaType.VOICE);
        VideoCall received = call(
                32L, emily, myClone, today.atTime(11, 0), 1200, CallMediaType.VOICE);
        VideoCall previous = call(
                21L, currentUser, sarahClone, today.minusWeeks(1).atTime(10, 0),
                4000, CallMediaType.VOICE);

        when(videoCallRepository.findRecentHistory(
                eq(currentUserUuid),
                eq(VideoCallStatus.COMPLETED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(sent, received))
                .thenReturn(List.of(previous));

        HistoryResDTO.WeeklySummaryDTO result = historyService.getWeeklySummary(currentUserUuid);

        assertThat(result.totalTalkTimeSec()).isEqualTo(4800);
        assertThat(result.sentCallCount()).isEqualTo(1);
        assertThat(result.receivedCallCount()).isEqualTo(1);
        assertThat(result.changeRate()).isEqualTo(20);
        assertThat(result.trend()).isEqualTo(HistoryResDTO.WeeklyTrend.UP);
        assertThat(result.comparable()).isTrue();
        assertThat(result.period().startedAt().getDayOfWeek())
                .isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(result.period().nextResetAt()).isEqualTo(result.period().endedAt());
    }

    @Test
    void getTalkLogsReturnsCorrectedMessageAndEditPermission() {
        LocalDate today = LocalDate.now();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = user(currentUserUuid, "나", today.minusYears(26));
        User emily = user(UUID.randomUUID(), "Emily", today.minusYears(26));
        Clone myClone = clone(currentUser, 88);
        Clone emilyClone = clone(emily, 89);
        VideoCall receivedCall = call(
                20L, emily, myClone, today.atTime(11, 15), 765, CallMediaType.VOICE);
        TalkLog partnerLog = talkLog(101L, receivedCall, Speaker.USER, "안녕하세요", today.atTime(11, 15));
        TalkLog twinLog = talkLog(102L, receivedCall, Speaker.CLONE, "원본 답변", today.atTime(11, 16));
        twinLog.updateMessage("수정된 답변");

        when(videoCallRepository.findByIdWithParticipants(20L)).thenReturn(Optional.of(receivedCall));
        when(cloneRepository.findByUserUuid(emily.getUuid())).thenReturn(Optional.of(emilyClone));
        when(talkLogRepository.findAllByVideoCallIdOrderByStartedAtAscIdAsc(20L))
                .thenReturn(List.of(partnerLog, twinLog));
        when(videoCallRepository.countCallsBetweenUsersThrough(
                currentUserUuid,
                emily.getUuid(),
                VideoCallStatus.COMPLETED,
                receivedCall.getStartedAt()
        )).thenReturn(2L);

        HistoryResDTO.TalkLogListDTO result = historyService.getTalkLogs(currentUserUuid, 20L);

        assertThat(result.callNumber()).isEqualTo(2);
        assertThat(result.partner().name()).isEqualTo("Emily");
        assertThat(result.partner().twinSyncRate()).isEqualTo(89);
        assertThat(result.talkLogs()).extracting(HistoryResDTO.TalkLogDTO::speaker)
                .containsExactly(
                        HistoryResDTO.TalkLogSpeaker.PARTNER,
                        HistoryResDTO.TalkLogSpeaker.MY_TWIN
                );
        assertThat(result.talkLogs().get(0).editable()).isFalse();
        assertThat(result.talkLogs().get(1).editable()).isTrue();
        assertThat(result.talkLogs().get(1).edited()).isTrue();
        assertThat(result.talkLogs().get(1).message()).isEqualTo("수정된 답변");
    }

    @Test
    void updateTalkLogChangesMessageDirectly() {
        LocalDate today = LocalDate.now();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = user(currentUserUuid, "나", today.minusYears(26));
        User emily = user(UUID.randomUUID(), "Emily", today.minusYears(26));
        Clone myClone = clone(currentUser, 88);
        VideoCall receivedCall = call(
                20L, emily, myClone, today.atTime(11, 15), 765, CallMediaType.VOICE);
        TalkLog twinLog = talkLog(102L, receivedCall, Speaker.CLONE, "원본 답변", today.atTime(11, 16));

        when(videoCallRepository.findByIdWithParticipants(20L)).thenReturn(Optional.of(receivedCall));
        when(talkLogRepository.findByIdAndVideoCallId(102L, 20L)).thenReturn(Optional.of(twinLog));
        when(talkLogRepository.saveAndFlush(any(TalkLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HistoryResDTO.TalkLogDTO result = historyService.updateTalkLog(
                currentUserUuid,
                20L,
                102L,
                new HistoryReqDTO.UpdateTalkLogDTO("  수정된 답변  ")
        );

        assertThat(result.message()).isEqualTo("수정된 답변");
        assertThat(result.edited()).isTrue();
        assertThat(result.editable()).isTrue();
        assertThat(twinLog.getMessage()).isEqualTo("수정된 답변");
        assertThat(twinLog.isEdited()).isTrue();
        assertThat(twinLog.getEditedAt()).isNotNull();
        verify(talkLogRepository).saveAndFlush(twinLog);
    }

    @Test
    void updateTalkLogRejectsPartnersTwinAnswer() {
        LocalDate today = LocalDate.now();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = user(currentUserUuid, "나", today.minusYears(26));
        User sarah = user(UUID.randomUUID(), "Sarah", today.minusYears(28));
        Clone sarahClone = clone(sarah, 94);
        VideoCall sentCall = call(
                11L, currentUser, sarahClone, today.atTime(14, 30), 503, CallMediaType.VIDEO);
        TalkLog partnerTwinLog = talkLog(
                103L, sentCall, Speaker.CLONE, "상대 Twin 답변", today.atTime(14, 31));

        when(videoCallRepository.findByIdWithParticipants(11L)).thenReturn(Optional.of(sentCall));
        when(talkLogRepository.findByIdAndVideoCallId(103L, 11L))
                .thenReturn(Optional.of(partnerTwinLog));

        assertThatThrownBy(() -> historyService.updateTalkLog(
                currentUserUuid,
                11L,
                103L,
                new HistoryReqDTO.UpdateTalkLogDTO("수정 시도")
        )).isInstanceOfSatisfying(
                GeneralException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(GeneralErrorCode.TALK_LOG_UPDATE_FORBIDDEN)
        );
    }

    private User user(UUID uuid, String name, LocalDate birthDate) {
        return User.builder()
                .uuid(uuid)
                .email(uuid + "@example.com")
                .passwordHash("password")
                .name(name)
                .birthDate(birthDate)
                .build();
    }

    private Clone clone(User user, int syncRate) {
        return Clone.builder()
                .user(user)
                .syncRate(syncRate)
                .build();
    }

    private VideoCall call(
            Long id,
            User caller,
            Clone targetClone,
            LocalDateTime startedAt,
            int durationSec,
            CallMediaType mediaType
    ) {
        VideoCall call = mock(VideoCall.class);
        when(call.getId()).thenReturn(id);
        when(call.getUser()).thenReturn(caller);
        when(call.getClone()).thenReturn(targetClone);
        when(call.getStartedAt()).thenReturn(startedAt);
        when(call.getDurationSec()).thenReturn(durationSec);
        when(call.getMediaType()).thenReturn(mediaType);
        when(call.getStatus()).thenReturn(VideoCallStatus.COMPLETED);
        return call;
    }

    private CallMatchAnalysis analysis(
            VideoCall call,
            int twinSimilarity,
            List<String> summaryPoints
    ) {
        return CallMatchAnalysis.builder()
                .videoCall(call)
                .twinSimilarity(twinSimilarity)
                .summaryPoints(summaryPoints)
                .status(CallMatchAnalysisStatus.COMPLETED)
                .build();
    }

    private TalkLog talkLog(
            Long id,
            VideoCall call,
            Speaker speaker,
            String message,
            LocalDateTime startedAt
    ) {
        return TalkLog.builder()
                .id(id)
                .videoCall(call)
                .speaker(speaker)
                .message(message)
                .startedAt(startedAt)
                .endedAt(startedAt.plusSeconds(3))
                .build();
    }
}
