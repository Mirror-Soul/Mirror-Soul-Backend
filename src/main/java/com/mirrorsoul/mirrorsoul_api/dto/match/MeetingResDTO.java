package com.mirrorsoul.mirrorsoul_api.dto.match;

import com.mirrorsoul.mirrorsoul_api.domain.enums.MeetingRequestStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

public class MeetingResDTO {
    @Builder
    public record RequestListDTO(int totalCount, List<RequestDTO> requests) {
    }

    @Builder
    public record RequestDTO(
            Long requestId, UUID senderUserUuid, String name, Integer age,
            String profileImageUrl, LocalDateTime lastActiveAt, Integer twinSimilarity,
            String message, String conversationSummary, List<String> summaryPoints,
            LocalDateTime requestedAt
    ) {
    }

    @Builder
    public record CreatedDTO(Long requestId, MeetingRequestStatus status, LocalDateTime requestedAt) {
    }

    @Builder
    public record RespondedDTO(Long requestId, MeetingRequestStatus status, LocalDateTime respondedAt) {
    }

    @Builder
    public record AcceptedDTO(
            Long requestId, MeetingRequestStatus status, Long chatRoomId,
            boolean chatRoomCreated, LocalDateTime respondedAt
    ) {
    }
}
