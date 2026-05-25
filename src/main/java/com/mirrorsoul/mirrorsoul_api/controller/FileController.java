package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlResDTO;
import com.mirrorsoul.mirrorsoul_api.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "File", description = "파일 업로드 지원 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(
            summary = "S3 Presigned URL 발급",
            description = "프론트엔드가 S3에 직접 PUT 업로드할 수 있도록 Presigned URL, fileUrl, objectKey를 발급합니다."
    )
    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResDTO> createPresignedUrl(@Valid @RequestBody PresignedUrlReqDTO request) {
        return ApiResponse.onSuccess(
                "Presigned URL 생성에 성공했습니다.",
                fileService.createPresignedUrl(request)
        );
    }

}
