package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingDtos.*;

@RestController @RequestMapping("/payments") @RequiredArgsConstructor
public class PaymentController {
    private final BillingService billing;
    private final BillingReadService reads;
    @PostMapping("/verify")
    public ApiResponse<PaymentView> verify(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody Verify request) {
        return ApiResponse.onSuccess("스토어 구매 처리 결과입니다.", billing.verify(user.getId(), request));
    }
    @GetMapping("/talk-time")
    public ApiResponse<Balance> balance(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.onSuccess("대화 시간 잔액입니다.", reads.balance(user.getId()));
    }
}
