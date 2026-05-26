package com.mirrorsoul.mirrorsoul_api.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PresignedUrlReqDTO(
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
