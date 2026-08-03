package com.mirrorsoul.mirrorsoul_api.dto.history;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class HistoryReqDTO {

    public enum HistoryType {
        ALL,
        RECEIVED,
        SENT
    }

    public record UpdateTalkLogDTO(
            @NotBlank(message = "수정할 답변은 필수입니다.")
            @Size(max = 2000, message = "답변은 2000자 이하여야 합니다.")
            String message
    ) {
    }
}
