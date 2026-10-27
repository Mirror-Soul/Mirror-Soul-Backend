package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingDtos.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@RestController @RequestMapping("/admin") @RequiredArgsConstructor
public class AdminBillingController {
    private final BillingReadService reads;
    private final TalkTimeAdjustmentService adjustments;
    private final BillingJobService jobs;
    @GetMapping("/payments")
    public ApiResponse<Page<PaymentView>> payments(@RequestParam(required = false) Store store,
            @RequestParam(required = false) Long memberId, @RequestParam(required = false) Verification verificationStatus,
            @RequestParam(required = false) Grant grantStatus, @RequestParam(required = false) Refund refundStatus,
            @RequestParam(required = false) String transactionId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.onSuccess("결제 내역입니다.", reads.payments(store, memberId, verificationStatus, grantStatus, refundStatus, transactionId, page, size));
    }
    @GetMapping("/members/{id}/talk-time-ledger")
    public ApiResponse<Page<LedgerView>> ledger(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.onSuccess("대화 시간 원장입니다.", reads.ledger(id, page, size));
    }
    @PostMapping("/talk-time-adjustments")
    public ApiResponse<LedgerView> adjust(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody Adjustment request) {
        return ApiResponse.onSuccess("대화 시간 보정 결과입니다.", adjustments.adjust(user.getId(), request));
    }
    @GetMapping("/payments/{id}/events")
    public ApiResponse<Page<JobView>> events(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.onSuccess("결제 처리 작업입니다.", reads.jobs(id, page, size));
    }
    @PostMapping("/payments/events/{id}/retry")
    public ApiResponse<Void> retry(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
                                  @Valid @RequestBody Retry request) {
        jobs.retry(user.getId(), id, request.idempotencyKey());
        return ApiResponse.onSuccess("재처리를 예약했습니다.");
    }
}
