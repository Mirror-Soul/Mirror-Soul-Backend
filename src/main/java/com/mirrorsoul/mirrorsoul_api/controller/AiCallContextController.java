package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiCallContextDTO;
import com.mirrorsoul.mirrorsoul_api.service.AiCallContextService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/ai/calls")
@RequiredArgsConstructor
public class AiCallContextController {

    private final AiCallContextService aiCallContextService;

    @GetMapping("/{call-id}/context")
    public ApiResponse<AiCallContextDTO> getContext(
            @PathVariable("call-id") Long callId
    ) {
        return ApiResponse.onSuccess(
                "AI 통화 컨텍스트 조회에 성공했습니다.",
                aiCallContextService.getContext(callId)
        );
    }
}
