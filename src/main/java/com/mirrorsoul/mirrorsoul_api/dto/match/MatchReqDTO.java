package com.mirrorsoul.mirrorsoul_api.dto.match;

import jakarta.validation.constraints.NotNull;

public class MatchReqDTO {
    public record UpdateMatchingStatusDTO(
            @NotNull(message = "matchingEnabled는 필수입니다.") Boolean matchingEnabled
    ) {
    }
}
