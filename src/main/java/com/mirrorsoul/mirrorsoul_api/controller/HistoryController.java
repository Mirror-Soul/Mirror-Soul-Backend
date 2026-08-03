package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryResDTO;
import com.mirrorsoul.mirrorsoul_api.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "History", description = "History API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @Operation(
            summary = "최근 7일 통화 내역 조회",
            description = "최근 7일간의 통화 내역을 통화 방향으로 필터링하여 날짜별로 조회합니다. 쿼리 파라미터 ENUM은 ALL, RECEIVED, SENT"
    )
    @GetMapping("/calls")
    public ApiResponse<HistoryResDTO.CallHistoryListDTO> getCallHistory(
            @RequestParam(defaultValue = "ALL") HistoryReqDTO.HistoryType type,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "최근 7일 통화 내역 조회에 성공했습니다.",
                historyService.getCallHistory(currentUser.getUuid(), type)
        );
    }

    @Operation(
            summary = "주간 통화 통계 조회",
            description = "월요일 00시를 기준으로 이번 주 통화 시간과 받은/보낸 통화 수를 조회합니다."
    )
    @GetMapping("/weekly-summary")
    public ApiResponse<HistoryResDTO.WeeklySummaryDTO> getWeeklySummary(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "주간 통화 통계 조회에 성공했습니다.",
                historyService.getWeeklySummary(currentUser.getUuid())
        );
    }

    @Operation(summary = "통화 대화 내역 조회")
    @GetMapping("/calls/{call-id}/talk-logs")
    public ApiResponse<HistoryResDTO.TalkLogListDTO> getTalkLogs(
            @PathVariable("call-id") Long callId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "통화 대화 내역 조회에 성공했습니다.",
                historyService.getTalkLogs(currentUser.getUuid(), callId)
        );
    }

    @Operation(
            summary = "대화내역 수정 (내 Twin 답변 수정)",
            description = "통화에서 내 Twin이 답변한 대화 내용만 수정할 수 있습니다."
    )
    @PatchMapping("/calls/{call-id}/talk-logs/{talk-log-id}")
    public ApiResponse<HistoryResDTO.TalkLogDTO> updateTalkLog(
            @PathVariable("call-id") Long callId,
            @PathVariable("talk-log-id") Long talkLogId,
            @Valid @RequestBody HistoryReqDTO.UpdateTalkLogDTO request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "Twin 답변 수정에 성공했습니다.",
                historyService.updateTalkLog(
                        currentUser.getUuid(),
                        callId,
                        talkLogId,
                        request
                )
        );
    }
}
