package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallResDTO;
import com.mirrorsoul.mirrorsoul_api.service.CallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Call", description = "유저-클론 통화 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @Operation(summary = "클론에게 음성/영상 통화 걸기")
    @PostMapping("/clones/{clone-user-uuid}")
    public ApiResponse<CallResDTO.StartCallDTO> startCloneCall(
            @PathVariable("clone-user-uuid") UUID cloneUserUuid,
            @Valid @RequestBody CallReqDTO.StartCallDTO request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "클론 통화 세션 생성에 성공했습니다.",
                callService.startCloneCall(cloneUserUuid, request, currentUser.getUuid())
        );
    }

    @Operation(summary = "통화 연결 완료 처리")
    @PatchMapping("/{call-id}/in-progress")
    public ApiResponse<Void> markInProgress(@PathVariable("call-id") Long callId) {
        callService.markInProgress(callId);
        return ApiResponse.onSuccess("통화 연결 상태로 변경했습니다.", null);
    }

    @Operation(summary = "통화 종료")
    @PostMapping("/{call-id}/end")
    public ApiResponse<CallResDTO.EndCallDTO> endCall(
            @PathVariable("call-id") Long callId,
            @RequestBody(required = false) CallReqDTO.EndCallDTO request
    ) {
        return ApiResponse.onSuccess(
                "통화 종료에 성공했습니다.",
                callService.endCall(callId, request)
        );
    }
}