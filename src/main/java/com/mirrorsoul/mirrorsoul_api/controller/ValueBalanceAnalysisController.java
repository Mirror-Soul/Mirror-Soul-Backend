package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.valuebalance.ValueBalanceAnalysisCallbackDTO;
import com.mirrorsoul.mirrorsoul_api.service.ValueBalanceAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/value-balance/analysis-jobs")
@RequiredArgsConstructor
public class ValueBalanceAnalysisController {
    private final ValueBalanceAnalysisService service;

    @PostMapping("/{jobId}/complete")
    public ApiResponse<Void> complete(
            @PathVariable Long jobId,
            @RequestHeader("X-Value-Balance-Callback-Secret") String callbackSecret,
            @Valid @RequestBody ValueBalanceAnalysisCallbackDTO request) {
        service.complete(jobId, callbackSecret, request);
        return ApiResponse.onSuccess("Value balance analysis completed.", null);
    }
}
