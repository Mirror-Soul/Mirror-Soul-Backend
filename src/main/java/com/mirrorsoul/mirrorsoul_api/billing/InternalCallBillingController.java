package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.service.CallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/ai/calls") @RequiredArgsConstructor
public class InternalCallBillingController {
    private final CallService calls;
    @PostMapping("/{callId}/billing-events")
    public ApiResponse<CallBillingDtos.Result> event(@PathVariable Long callId, @Valid @RequestBody CallBillingDtos.Event event) {
        return ApiResponse.onSuccess("통화 과금 이벤트 처리 결과입니다.", calls.billingEvent(callId, event));
    }
}
