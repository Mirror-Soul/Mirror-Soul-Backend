package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.CallMatchAnalysis;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.TalkLog;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMatchAnalysisStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Speaker;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CallMatchAnalysisRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkLogRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {

    private static final int HISTORY_DAYS = 7;

    private final VideoCallRepository videoCallRepository;
    private final CallMatchAnalysisRepository callMatchAnalysisRepository;
    private final CloneRepository cloneRepository;
    private final TalkLogRepository talkLogRepository;

    public HistoryResDTO.CallHistoryListDTO getCallHistory(
            UUID currentUserUuid,
            HistoryReqDTO.HistoryType type
    ) {
        LocalDate today = LocalDate.now();
        LocalDateTime historyStart = today.minusDays(HISTORY_DAYS - 1L).atStartOfDay();
        LocalDateTime historyEnd = today.plusDays(1).atStartOfDay();
        List<VideoCall> recentCalls = videoCallRepository.findRecentHistory(
                currentUserUuid,
                VideoCallStatus.COMPLETED,
                historyStart,
                historyEnd
        );

        long sentCount = recentCalls.stream()
                .filter(call -> isSent(call, currentUserUuid))
                .count();
        long receivedCount = recentCalls.size() - sentCount;

        List<VideoCall> filteredCalls = recentCalls.stream()
                .filter(call -> matchesType(call, currentUserUuid, type))
                .toList();
        Map<Long, CallMatchAnalysis> analysesByCallId = loadAnalyses(filteredCalls);
        Map<UUID, Clone> clonesByUserUuid = loadPartnerClones(filteredCalls, currentUserUuid);

        Map<LocalDate, List<HistoryResDTO.CallHistoryDTO>> historiesByDate = filteredCalls.stream()
                .map(call -> toCallHistoryDTO(
                        call,
                        currentUserUuid,
                        analysesByCallId.get(call.getId()),
                        clonesByUserUuid,
                        today
                ))
                .collect(Collectors.groupingBy(
                        history -> history.startedAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<HistoryResDTO.CallHistoryGroupDTO> groups = historiesByDate.entrySet().stream()
                .map(entry -> HistoryResDTO.CallHistoryGroupDTO.builder()
                        .date(entry.getKey())
                        .histories(entry.getValue())
                        .build())
                .toList();

        return HistoryResDTO.CallHistoryListDTO.builder()
                .summary(HistoryResDTO.CallHistorySummaryDTO.builder()
                        .totalCount(recentCalls.size())
                        .receivedCount(receivedCount)
                        .sentCount(sentCount)
                        .build())
                .groups(groups)
                .build();
    }

    public HistoryResDTO.WeeklySummaryDTO getWeeklySummary(UUID currentUserUuid) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        LocalDateTime nextWeekStart = weekStart.plusWeeks(1);
        LocalDateTime previousWeekStart = weekStart.minusWeeks(1);
        LocalDateTime previousComparisonEnd = previousWeekStart
                .plusSeconds(Duration.between(weekStart, now).getSeconds());

        List<VideoCall> currentWeekCalls = videoCallRepository.findRecentHistory(
                currentUserUuid,
                VideoCallStatus.COMPLETED,
                weekStart,
                now
        );
        List<VideoCall> previousWeekCalls = videoCallRepository.findRecentHistory(
                currentUserUuid,
                VideoCallStatus.COMPLETED,
                previousWeekStart,
                previousComparisonEnd
        );

        long currentTalkTimeSec = sumDurationSec(currentWeekCalls);
        long previousTalkTimeSec = sumDurationSec(previousWeekCalls);
        long sentCallCount = currentWeekCalls.stream()
                .filter(call -> isSent(call, currentUserUuid))
                .count();
        long receivedCallCount = currentWeekCalls.size() - sentCallCount;
        boolean comparable = previousTalkTimeSec > 0;
        Integer changeRate = comparable
                ? (int) Math.round(
                        (currentTalkTimeSec - previousTalkTimeSec) * 100.0 / previousTalkTimeSec)
                : null;

        return HistoryResDTO.WeeklySummaryDTO.builder()
                .period(HistoryResDTO.WeeklyPeriodDTO.builder()
                        .startedAt(weekStart)
                        .endedAt(nextWeekStart)
                        .nextResetAt(nextWeekStart)
                        .build())
                .totalTalkTimeSec(currentTalkTimeSec)
                .receivedCallCount(receivedCallCount)
                .sentCallCount(sentCallCount)
                .changeRate(changeRate)
                .trend(toWeeklyTrend(changeRate))
                .comparable(comparable)
                .build();
    }

    private long sumDurationSec(List<VideoCall> calls) {
        return calls.stream()
                .map(VideoCall::getDurationSec)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private HistoryResDTO.WeeklyTrend toWeeklyTrend(Integer changeRate) {
        if (changeRate == null) {
            return HistoryResDTO.WeeklyTrend.NO_DATA;
        }
        if (changeRate > 0) {
            return HistoryResDTO.WeeklyTrend.UP;
        }
        if (changeRate < 0) {
            return HistoryResDTO.WeeklyTrend.DOWN;
        }
        return HistoryResDTO.WeeklyTrend.SAME;
    }

    public HistoryResDTO.TalkLogListDTO getTalkLogs(UUID currentUserUuid, Long callId) {
        VideoCall call = requireAccessibleCall(currentUserUuid, callId);
        User partner = partnerOf(call, currentUserUuid);
        Clone partnerClone = isSent(call, currentUserUuid)
                ? call.getClone()
                : cloneRepository.findByUserUuid(partner.getUuid()).orElse(null);
        List<TalkLog> talkLogs = talkLogRepository
                .findAllByVideoCallIdOrderByStartedAtAscIdAsc(callId);

        List<HistoryResDTO.TalkLogDTO> talkLogDTOs = talkLogs.stream()
                .map(talkLog -> toTalkLogDTO(call, talkLog, currentUserUuid))
                .toList();
        long callNumber = videoCallRepository.countCallsBetweenUsersThrough(
                currentUserUuid,
                partner.getUuid(),
                VideoCallStatus.COMPLETED,
                call.getStartedAt()
        );

        return HistoryResDTO.TalkLogListDTO.builder()
                .callId(call.getId())
                .callNumber(Math.toIntExact(callNumber))
                .partner(HistoryResDTO.PartnerDTO.builder()
                        .userUuid(partner.getUuid())
                        .name(partner.getName())
                        .age(calculateAge(partner.getBirthDate(), LocalDate.now()))
                        .profileImageUrl(partner.getProfileImageUrl())
                        .twinSyncRate(partnerClone == null ? null : partnerClone.getSyncRate())
                        .build())
                .description(isSent(call, currentUserUuid)
                        ? partner.getName() + "의 Twin과 대화"
                        : partner.getName() + "와 내 Twin의 대화")
                .startedAt(call.getStartedAt())
                .talkLogs(talkLogDTOs)
                .build();
    }

    @Transactional
    public HistoryResDTO.TalkLogDTO updateTalkLog(
            UUID currentUserUuid,
            Long callId,
            Long talkLogId,
            HistoryReqDTO.UpdateTalkLogDTO request
    ) {
        VideoCall call = requireAccessibleCall(currentUserUuid, callId);
        TalkLog talkLog = talkLogRepository.findByIdAndVideoCallId(talkLogId, callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.TALK_LOG_NOT_FOUND));
        if (!ownsTargetClone(call, currentUserUuid) || talkLog.getSpeaker() != Speaker.CLONE) {
            throw new GeneralException(GeneralErrorCode.TALK_LOG_UPDATE_FORBIDDEN);
        }

        String correctedMessage = request.message().trim();
        talkLog.updateMessage(correctedMessage);
        TalkLog savedTalkLog = talkLogRepository.saveAndFlush(talkLog);

        return toTalkLogDTO(call, savedTalkLog, currentUserUuid);
    }

    private VideoCall requireAccessibleCall(UUID currentUserUuid, Long callId) {
        VideoCall call = videoCallRepository.findByIdWithParticipants(callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));
        if (call.getStatus() != VideoCallStatus.COMPLETED) {
            throw new GeneralException(GeneralErrorCode.CALL_NOT_FOUND);
        }
        boolean participant = isSent(call, currentUserUuid) || ownsTargetClone(call, currentUserUuid);
        if (!participant) {
            throw new GeneralException(GeneralErrorCode.CALL_ACCESS_DENIED);
        }
        return call;
    }

    private HistoryResDTO.TalkLogDTO toTalkLogDTO(
            VideoCall call,
            TalkLog talkLog,
            UUID currentUserUuid
    ) {
        boolean editable = ownsTargetClone(call, currentUserUuid)
                && talkLog.getSpeaker() == Speaker.CLONE;
        return HistoryResDTO.TalkLogDTO.builder()
                .talkLogId(talkLog.getId())
                .speaker(toTalkLogSpeaker(call, talkLog, currentUserUuid))
                .message(talkLog.getMessage())
                .startedAt(talkLog.getStartedAt())
                .endedAt(talkLog.getEndedAt())
                .editable(editable)
                .edited(talkLog.isEdited())
                .editedAt(talkLog.getEditedAt())
                .build();
    }

    private HistoryResDTO.TalkLogSpeaker toTalkLogSpeaker(
            VideoCall call,
            TalkLog talkLog,
            UUID currentUserUuid
    ) {
        if (isSent(call, currentUserUuid)) {
            return talkLog.getSpeaker() == Speaker.USER
                    ? HistoryResDTO.TalkLogSpeaker.ME
                    : HistoryResDTO.TalkLogSpeaker.PARTNER_TWIN;
        }
        return talkLog.getSpeaker() == Speaker.USER
                ? HistoryResDTO.TalkLogSpeaker.PARTNER
                : HistoryResDTO.TalkLogSpeaker.MY_TWIN;
    }

    private boolean ownsTargetClone(VideoCall call, UUID currentUserUuid) {
        return call.getClone().getUser().getUuid().equals(currentUserUuid);
    }

    private Map<Long, CallMatchAnalysis> loadAnalyses(List<VideoCall> calls) {
        if (calls.isEmpty()) {
            return Map.of();
        }
        List<Long> callIds = calls.stream().map(VideoCall::getId).toList();
        return callMatchAnalysisRepository.findAllByVideoCallIdIn(callIds).stream()
                .filter(analysis -> analysis.getStatus() == CallMatchAnalysisStatus.COMPLETED)
                .collect(Collectors.toMap(
                        analysis -> analysis.getVideoCall().getId(),
                        Function.identity()
                ));
    }

    private Map<UUID, Clone> loadPartnerClones(List<VideoCall> calls, UUID currentUserUuid) {
        List<UUID> partnerUuids = calls.stream()
                .map(call -> partnerOf(call, currentUserUuid).getUuid())
                .distinct()
                .toList();
        if (partnerUuids.isEmpty()) {
            return Map.of();
        }
        return cloneRepository.findAllByUserUuidIn(partnerUuids).stream()
                .collect(Collectors.toMap(clone -> clone.getUser().getUuid(), Function.identity()));
    }

    private HistoryResDTO.CallHistoryDTO toCallHistoryDTO(
            VideoCall call,
            UUID currentUserUuid,
            CallMatchAnalysis analysis,
            Map<UUID, Clone> clonesByUserUuid,
            LocalDate today
    ) {
        boolean sent = isSent(call, currentUserUuid);
        HistoryReqDTO.HistoryType type = sent
                ? HistoryReqDTO.HistoryType.SENT
                : HistoryReqDTO.HistoryType.RECEIVED;
        User partner = partnerOf(call, currentUserUuid);
        Clone partnerClone = clonesByUserUuid.get(partner.getUuid());

        return HistoryResDTO.CallHistoryDTO.builder()
                .callId(call.getId())
                .type(type)
                .partner(HistoryResDTO.PartnerDTO.builder()
                        .userUuid(partner.getUuid())
                        .name(partner.getName())
                        .age(calculateAge(partner.getBirthDate(), today))
                        .profileImageUrl(partner.getProfileImageUrl())
                        .twinSyncRate(partnerClone == null ? null : partnerClone.getSyncRate())
                        .build())
                .description(sent ? "내가 시작한 통화" : partner.getName() + "의 Twin과 통화")
                .mediaType(call.getMediaType())
                .durationSec(call.getDurationSec())
                .matchTarget(sent
                        ? HistoryResDTO.MatchTarget.PARTNER_TWIN
                        : HistoryResDTO.MatchTarget.MY_TWIN)
                .matchScore(analysis == null ? null : analysis.getTwinSimilarity())
                .topics(analysis == null || analysis.getSummaryPoints() == null
                        ? List.of() : analysis.getSummaryPoints())
                .startedAt(call.getStartedAt())
                .isNew(call.getStartedAt().toLocalDate().equals(today))
                .build();
    }

    private boolean matchesType(
            VideoCall call,
            UUID currentUserUuid,
            HistoryReqDTO.HistoryType type
    ) {
        return type == HistoryReqDTO.HistoryType.ALL
                || (type == HistoryReqDTO.HistoryType.SENT && isSent(call, currentUserUuid))
                || (type == HistoryReqDTO.HistoryType.RECEIVED && !isSent(call, currentUserUuid));
    }

    private boolean isSent(VideoCall call, UUID currentUserUuid) {
        return call.getUser().getUuid().equals(currentUserUuid);
    }

    private User partnerOf(VideoCall call, UUID currentUserUuid) {
        return isSent(call, currentUserUuid)
                ? call.getClone().getUser()
                : call.getUser();
    }

    private Integer calculateAge(LocalDate birthDate, LocalDate today) {
        return birthDate == null ? null : Period.between(birthDate, today).getYears();
    }
}
