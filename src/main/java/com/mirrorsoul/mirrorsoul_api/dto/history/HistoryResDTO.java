package com.mirrorsoul.mirrorsoul_api.dto.history;

import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

public class HistoryResDTO {

    public enum MatchTarget {
        MY_TWIN,
        PARTNER_TWIN
    }

    @Builder
    public record CallHistoryListDTO(
            CallHistorySummaryDTO summary,
            List<CallHistoryDTO> histories,
            PageDTO page
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
    public record PageDTO(
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
