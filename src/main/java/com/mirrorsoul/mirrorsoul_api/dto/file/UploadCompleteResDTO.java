package com.mirrorsoul.mirrorsoul_api.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;

public record UploadCompleteResDTO(
        @Schema(description = "Uploaded file type")
        String fileType,

        @Schema(description = "S3 object key")
        String objectKey,

        @Schema(description = "S3 file URL")
        String fileUrl,

        @Schema(description = "Whether upload completion was processed")
        Boolean uploaded
) {
}
