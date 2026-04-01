package com.mirrorsoul.mirrorsoul_api.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;

public record PresignedUrlResponse(
        @Schema(description = "S3 직접 PUT 업로드에 사용할 Presigned URL")
        String presignedUrl,

        @Schema(description = "업로드 완료 후 저장/전달에 사용할 S3 파일 URL")
        String fileUrl,

        @Schema(description = "DB 저장 또는 AI 서버 전달에 사용할 S3 object key")
        String objectKey
) {
}
