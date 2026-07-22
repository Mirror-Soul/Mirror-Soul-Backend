package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈 화면 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    @Operation(summary = "추천 리스트 조회 api", description = "추천 받은 유저 리스트를 조회합니다.")
    @GetMapping("/recommend")
    public ApiResponse<Void> recommend() {
        return null;
    }
}
