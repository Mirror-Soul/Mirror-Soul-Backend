package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.RecommendResDTO;
import com.mirrorsoul.mirrorsoul_api.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈 화면 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final RecommendService recommendService;

    @Operation(summary = "추천 리스트 조회 api", description = "추천 받은 유저 리스트를 조회합니다.")
    @GetMapping("/recommend")
    public ApiResponse<RecommendResDTO.RecommendationSliceDTO> recommend(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.onSuccess(
                "추천 유저 리스트 조회에 성공했습니다.",
                recommendService.getRecommendations(currentUser.getUuid(), pageable)
        );
    }
}
