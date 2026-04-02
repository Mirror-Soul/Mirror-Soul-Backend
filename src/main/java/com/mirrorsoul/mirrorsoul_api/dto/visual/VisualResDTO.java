package com.mirrorsoul.mirrorsoul_api.dto.visual;

public record VisualResDTO(
        Long faceFileId,
        Long userId,
        String fileUrl,
        String objectKey,
        Boolean saved
) {
}
