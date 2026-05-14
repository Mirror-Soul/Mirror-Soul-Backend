package com.mirrorsoul.mirrorsoul_api.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PresignedUrlReqDTO(
        @Schema(description = "사용자 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "userUuid is required.")
        UUID userUuid,

        @Schema(description = "업로드할 원본 파일명", example = "interview-answer-1.m4a")
        @NotBlank(message = "fileName is required.")
        String fileName,

        @Schema(description = "업로드할 파일의 Content-Type", example = "audio/mp4")
        @NotBlank(message = "contentType is required.")
        String contentType,

        @Schema(description = "업로드 저장 디렉터리", example = "interviews", allowableValues = {"interviews", "face-videos", "job-certifications"})
        @NotBlank(message = "directory is required.")
        String directory
) {
}
