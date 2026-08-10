package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.RecommendResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeResDTO;
import com.mirrorsoul.mirrorsoul_api.service.HomeService;
import com.mirrorsoul.mirrorsoul_api.service.RecommendService;
import com.mirrorsoul.mirrorsoul_api.service.RecommendationDetailService;
import com.mirrorsoul.mirrorsoul_api.service.SwipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Home", description = "홈 화면 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final RecommendService recommendService;
    private final HomeService homeService;
    private final SwipeService swipeService;
    private final RecommendationDetailService recommendationDetailService;

    @Operation(summary = "홈 화면 조회", description = "잔여 대화 시간과 현재 선호 지역을 조회합니다.")
    @GetMapping
    public ApiResponse<HomeResDTO.HomeDTO> getHome(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "홈 화면 조회에 성공했습니다.",
                homeService.getHome(currentUser.getUuid())
        );
    }

    @Operation(summary = "대화 시간 충전", description = "로그인 사용자의 대화 시간을 초 단위로 충전합니다.")
    @PostMapping("/talk-time/refill")
    public ApiResponse<HomeResDTO.TalkTimeDTO> refillTalkTime(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody HomeReqDTO.RefillTalkTimeDTO request
    ) {
        throw notImplemented();
    }

    @Operation(summary = "선호 지역 설정", description = "홈 화면 추천에 사용할 시군구를 1개 이상 3개 이하로 설정합니다.")
    @PutMapping("/preferred-regions")
    public ApiResponse<HomeResDTO.PreferredRegionsDTO> updatePreferredRegions(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody HomeReqDTO.UpdatePreferredRegionsDTO request
    ) {
        return ApiResponse.onSuccess(
                "선호 지역 설정에 성공했습니다.",
                homeService.updatePreferredRegions(currentUser.getUuid(), request)
        );
    }

    @Operation(summary = "선호 지역 선택지 조회", description = "선호 지역 설정에 사용할 시군구 ID와 이름을 조회합니다.")
    @GetMapping("/preferred-regions/options")
    public ApiResponse<HomeResDTO.SigunguOptionsDTO> getPreferredRegionOptions() {
        return ApiResponse.onSuccess(
                "선호 지역 선택지 조회에 성공했습니다.",
                homeService.getSigunguOptions()
        );
    }

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

    // TODO Big Five(개방성, 성실성, 외향성, 우호성, 신경증) 데이터 모델이 확정되면 성격 궁합 지표를 응답에 추가합니다.
    // TODO 사용자 쌍 기준 AI 궁합 분석 생성 및 저장 정책이 확정되면 분석 문구를 응답에 추가합니다.
    @Operation(summary = "추천 유저 상세 조회", description = "추천 유저의 프로필과 Twin 정보를 조회합니다.")
    @GetMapping("/recommendations/{target-user-uuid}")
    public ApiResponse<HomeResDTO.RecommendationDetailDTO> getRecommendationDetail(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable("target-user-uuid") UUID targetUserUuid
    ) {
        return ApiResponse.onSuccess(
                "추천 유저 상세 조회에 성공했습니다.",
                recommendationDetailService.getDetail(targetUserUuid)
        );
    }

    @Operation(summary = "추천 유저 스와이프", description = "추천 유저를 넘긴 기록을 저장합니다. LIKE 기능은 포함하지 않습니다.")
    @PostMapping("/recommendations/{target-user-uuid}/swipe")
    public ApiResponse<Void> swipeRecommendation(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable("target-user-uuid") UUID targetUserUuid
    ) {
        swipeService.swipe(currentUser.getUuid(), targetUserUuid);
        return ApiResponse.onSuccess("추천 유저 스와이프 처리에 성공했습니다.");
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "서비스 로직 구현 예정입니다.");
    }
}
