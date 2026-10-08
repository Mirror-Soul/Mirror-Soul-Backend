package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationDecisionResponse;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationRejectRequest;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationReviewDetail;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationReviewSummary;
import com.mirrorsoul.mirrorsoul_api.service.JobVerificationReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Job Verification", description = "관리자 직업 서류 심사 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/job-verifications")
@RequiredArgsConstructor
public class AdminJobVerificationController {

    private final JobVerificationReviewService service;

    @Operation(summary = "심사 대기 요청 목록")
    @GetMapping
    public ApiResponse<Page<JobVerificationReviewSummary>> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess("심사 대기 요청 목록입니다.", service.pending(page, size));
    }

    @Operation(summary = "심사 요청 상세 및 사진 조회 URL")
    @GetMapping("/{requestId}")
    public ApiResponse<JobVerificationReviewDetail> detail(@PathVariable Long requestId) {
        return ApiResponse.onSuccess("심사 요청 상세입니다.", service.detail(requestId));
    }

    @Operation(summary = "직업 증빙 서류 승인", description = "서류 심사 완료이며 PASS 전 최종 인증은 아닙니다.")
    @PostMapping("/{requestId}/approve")
    public ApiResponse<JobVerificationDecisionResponse> approve(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess("서류 심사를 승인했습니다.",
                service.approve(currentUser.getUuid(), requestId));
    }

    @Operation(summary = "직업 증빙 서류 거부")
    @PostMapping("/{requestId}/reject")
    public ApiResponse<JobVerificationDecisionResponse> reject(
            @PathVariable Long requestId,
            @Valid @RequestBody JobVerificationRejectRequest body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess("서류 심사를 거부했습니다.",
                service.reject(currentUser.getUuid(), requestId, body.reason()));
    }
}
