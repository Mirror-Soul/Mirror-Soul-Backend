package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.history.HistoryResDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "History", description = "사용자 활동 내역 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    @Operation(summary = "통화 내역 조회")
    @GetMapping("/calls")
    public ApiResponse<HistoryResDTO.CallHistoryListDTO> getCallHistory(
            @RequestParam(defaultValue = "ALL") HistoryReqDTO.HistoryType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess("통화 내역 조회에 성공했습니다.", null);
    }
}
