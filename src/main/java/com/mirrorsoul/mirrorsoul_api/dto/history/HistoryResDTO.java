package com.mirrorsoul.mirrorsoul_api.dto.history;

import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

public class HistoryResDTO {

    public enum MatchTarget {
        MY_TWIN,
        PARTNER_TWIN
    }

    public enum TalkLogSpeaker {
        PARTNER,
        MY_TWIN,
        ME,
        PARTNER_TWIN
    }

    public enum WeeklyTrend {
        UP,
        DOWN,
        SAME,
        NO_DATA
    }

    @Builder
    public record CallHistoryListDTO(
            CallHistorySummaryDTO summary,
            List<CallHistoryGroupDTO> groups
    ) {
    }

    @Builder
    public record CallHistorySummaryDTO(
            long totalCount,
            long receivedCount,
            long sentCount
    ) {
    }

    @Builder
    public record CallHistoryGroupDTO(
            LocalDate date,
            List<CallHistoryDTO> histories
    ) {
    }

    @Builder
    public record CallHistoryDTO(
            Long callId,
            HistoryReqDTO.HistoryType type,
            PartnerDTO partner,
            String description,
            CallMediaType mediaType,
            Integer durationSec,
            MatchTarget matchTarget,
            Integer matchScore,
            List<String> topics,
            LocalDateTime startedAt,
            boolean isNew
    ) {
    }

    @Builder
    public record PartnerDTO(
            UUID userUuid,
            String name,
            Integer age,
            String profileImageUrl,
            Integer twinSyncRate
    ) {
    }

    @Builder
    public record TalkLogListDTO(
            Long callId,
            Integer callNumber,
            PartnerDTO partner,
            String description,
            LocalDateTime startedAt,
            List<TalkLogDTO> talkLogs
    ) {
    }

    @Builder
    public record TalkLogDTO(
            Long talkLogId,
            TalkLogSpeaker speaker,
            String message,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            boolean editable,
            boolean edited,
            LocalDateTime editedAt
    ) {
    }

    @Builder
    public record WeeklySummaryDTO(
            WeeklyPeriodDTO period,
            long totalTalkTimeSec,
            long receivedCallCount,
            long sentCallCount,
            Integer changeRate,
            WeeklyTrend trend,
            boolean comparable
    ) {
    }

    @Builder
    public record WeeklyPeriodDTO(
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime nextResetAt
    ) {
    }
}
