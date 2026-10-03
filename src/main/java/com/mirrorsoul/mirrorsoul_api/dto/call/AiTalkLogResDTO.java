package com.mirrorsoul.mirrorsoul_api.dto.call;

import java.util.UUID;

public class AiTalkLogResDTO {

    public record Saved(
            Long talkLogId,
            UUID eventId,
            boolean duplicated
    ) {
    }
}
