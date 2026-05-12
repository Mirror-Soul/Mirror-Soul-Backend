package com.mirrorsoul.mirrorsoul_api.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UploadCompleteReqDTO(
        @Schema(description = "S3 object key", example = "interviews/123/question1.wav")
        @NotBlank(message = "objectKey is required.")
        String objectKey,

        @Schema(description = "Uploaded file type", example = "INTERVIEW_AUDIO", allowableValues = {"INTERVIEW_AUDIO"})
        @NotBlank(message = "fileType is required.")
        String fileType
) {
}
