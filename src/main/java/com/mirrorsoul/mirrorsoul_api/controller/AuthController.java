package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.LoginRequest;
import com.mirrorsoul.mirrorsoul_api.dto.LoginResponse;
import com.mirrorsoul.mirrorsoul_api.dto.RefreshTokenRequest;
import com.mirrorsoul.mirrorsoul_api.dto.RefreshTokenResponse;
import com.mirrorsoul.mirrorsoul_api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.onSuccess("로그인에 성공했습니다.", authService.login(request));
    }

    @Operation(summary = "Access token 재발급", description = "Refresh token을 검증한 뒤 새로운 access token을 발급합니다.")
    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.onSuccess("Access token이 재발급되었습니다.", authService.refresh(request));
    }

    @Operation(summary = "로그아웃", description = "현재 access token 기준으로 사용자를 식별한 뒤 저장된 refresh token을 제거합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ApiResponse.onSuccess("로그아웃에 성공했습니다.");
    }
}
