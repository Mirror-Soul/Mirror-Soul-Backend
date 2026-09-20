package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.match.MatchResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.match.MatchReqDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.mirrorsoul.mirrorsoul_api.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Match", description = "매칭 화면 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "디지털 자아 매칭 상태 조회")
    @GetMapping("/status")
    public ApiResponse<MatchResDTO.MatchingStatusDTO> getMatchingStatus(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess("매칭 상태를 조회했습니다.",
                matchService.getMatchingStatus(currentUser.getUuid()));
    }

    @Operation(summary = "디지털 자아 매칭 ON/OFF 변경", description = "OFF로 설정하면 다른 사용자의 추천에서 제외되고 내 클론에 대한 새로운 음성/영상 통화가 차단됩니다. 이미 시작된 통화와 본인의 클론 통화는 유지됩니다.")
    @PatchMapping("/status")
    public ApiResponse<MatchResDTO.MatchingStatusDTO> updateMatchingStatus(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody MatchReqDTO.UpdateMatchingStatusDTO request
    ) {
        return ApiResponse.onSuccess("매칭 상태를 변경했습니다.",
                matchService.updateMatchingStatus(currentUser.getUuid(), request.matchingEnabled()));
    }

    @Operation(
            summary = "통화했던 Twin 목록 조회",
            description = "로그인 사용자가 통화를 완료한 Twin을 최근 통화 순으로 중복 없이 조회합니다."
    )
    @GetMapping("/twins")
    public ApiResponse<MatchResDTO.TwinListDTO> getTwins(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "통화했던 Twin 목록을 조회했습니다.",
                matchService.getTwins(currentUser.getUuid())
        );
    }
}
