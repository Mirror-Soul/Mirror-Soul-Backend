package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.service.CloneTrainingCallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/clone-training")
public class CloneTrainingController {
    private final CloneTrainingCallbackService service;

    @PostMapping("/{cloneId}/personality/complete")
    public ApiResponse<Void> completePersonality(@PathVariable Long cloneId,
            @RequestHeader("X-Clone-Training-Callback-Secret") String secret) {
        service.completePersonality(cloneId, secret);
        return ApiResponse.onSuccess("Personality and personal-information training completed.", null);
    }
}
