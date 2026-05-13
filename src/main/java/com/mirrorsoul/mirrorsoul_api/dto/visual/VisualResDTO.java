package com.mirrorsoul.mirrorsoul_api.dto.visual;

import java.util.UUID;

public record VisualResDTO(
        Long faceFileId,
        UUID userUuid,
        String fileUrl,
        String objectKey,
        Boolean saved
) {
}
