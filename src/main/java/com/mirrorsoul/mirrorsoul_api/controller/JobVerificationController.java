package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationSubmitRequest;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationSubmitResponse;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationStatusResponse;
import com.mirrorsoul.mirrorsoul_api.service.JobVerificationSubmissionService;
import com.mirrorsoul.mirrorsoul_api.service.JobVerificationReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Job Verification", description = "직업 인증 서류 제출 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/job-verifications")
@RequiredArgsConstructor
public class JobVerificationController {

    private final JobVerificationSubmissionService submissionService;
    private final JobVerificationReviewService reviewService;

    @Operation(summary = "내 최신 직업 서류 심사 상태")
    @GetMapping("/me")
    public ApiResponse<JobVerificationStatusResponse> myLatest(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess("직업 서류 심사 상태입니다.",
                reviewService.myLatest(currentUser.getUuid()));
    }

    @Operation(
            summary = "직업 인증 서류 제출",
            description = "job-certifications 경로에 업로드한 사진을 심사 대기 요청으로 접수합니다. "
                    + "승인은 서류 심사만 의미하며 PASS 본인확인 전에는 최종 직업 인증이 아닙니다."
    )
    @PostMapping
    public ApiResponse<JobVerificationSubmitResponse> submit(
            @Valid @RequestBody JobVerificationSubmitRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "직업 인증 심사가 접수되었습니다.",
                submissionService.submit(currentUser.getUuid(), request)
        );
    }
}
