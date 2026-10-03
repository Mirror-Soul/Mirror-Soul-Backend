package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiTalkLogReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiTalkLogResDTO;
import com.mirrorsoul.mirrorsoul_api.service.AiTalkLogService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/ai/calls")
@RequiredArgsConstructor
public class AiTalkLogController {

    private final AiTalkLogService aiTalkLogService;

    @PostMapping("/{call-id}/talk-logs")
    public ApiResponse<AiTalkLogResDTO.Saved> save(
            @PathVariable("call-id") Long callId,
            @Valid @RequestBody AiTalkLogReqDTO.Save request
    ) {
        AiTalkLogResDTO.Saved result = aiTalkLogService.save(callId, request);
        String message = result.duplicated()
                ? "이미 저장된 통화 대화 내역입니다."
                : "통화 대화 내역 저장에 성공했습니다.";
        return ApiResponse.onSuccess(message, result);
    }
}
