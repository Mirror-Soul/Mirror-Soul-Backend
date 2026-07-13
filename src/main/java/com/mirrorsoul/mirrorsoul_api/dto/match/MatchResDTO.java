package com.mirrorsoul.mirrorsoul_api.dto.match;

import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

public class MatchResDTO {

    @Builder
    public record TwinListDTO(
            int totalCount,
            List<TwinDTO> twins
    ) {
    }

    @Builder
    public record TwinDTO(
            UUID cloneUserUuid,
            String name,
            Integer age,
            String profileImageUrl,
            String twinAvatarImageUrl,
            Integer twinSyncRate,
            String twinSummary,
            Long latestCallId,
            CallMediaType latestCallMediaType,
            LocalDateTime lastCalledAt,
            int totalCallCount
    ) {
    }
}
