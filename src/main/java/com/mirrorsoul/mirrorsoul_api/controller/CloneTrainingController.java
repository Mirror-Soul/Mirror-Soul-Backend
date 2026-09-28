package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.service.CloneTrainingCallbackService;
import com.mirrorsoul.mirrorsoul_api.dto.ClonePersonalityCompleteRequest;
import com.mirrorsoul.mirrorsoul_api.dto.CloneVoiceCompleteRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/clone-training")
public class CloneTrainingController {
    private final CloneTrainingCallbackService service;

    @PostMapping("/{cloneId}/personality/complete")
    public ApiResponse<Void> completePersonality(@PathVariable Long cloneId,
            @RequestHeader(value = "X-Clone-Training-Callback-Secret", required = false) String secret,
            @Valid @RequestBody(required = false) ClonePersonalityCompleteRequest request) {
        service.completePersonality(cloneId, secret, request);
        return ApiResponse.onSuccess("Personality and personal-information training completed.", null);
    }

    @PostMapping("/{cloneId}/voice/complete")
    public ApiResponse<Void> completeVoice(@PathVariable Long cloneId,
            @RequestHeader(value = "X-Clone-Training-Callback-Secret", required = false) String secret,
            @Valid @RequestBody CloneVoiceCompleteRequest request) {
        service.completeVoice(cloneId, secret, request);
        return ApiResponse.onSuccess("Voice similarity updated.", null);
    }
}
