package com.mirrorsoul.mirrorsoul_api.dto.visual;

import jakarta.validation.constraints.NotBlank;

public record VisualReqDTO(
        String fileUrl,
        @NotBlank
        String objectKey
) {
}
