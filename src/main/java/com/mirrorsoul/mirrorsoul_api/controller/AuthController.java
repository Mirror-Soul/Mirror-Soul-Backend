package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.login.LoginReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.auth.PasswordResetReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.login.LoginResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.login.RefreshTokenReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.login.RefreshTokenResDTO;
import com.mirrorsoul.mirrorsoul_api.service.AuthService;
import com.mirrorsoul.mirrorsoul_api.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인 관련 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @Operation(summary = "로그인", description = """
            이메일과 비밀번호로 로그인합니다.
            - OnboardA - 회원가입 완료
            - OnboardB - 기본 프로필 완료
            - OnboardC - 성격 유형 완료
            - OnboardD - AI 음성 인터뷰 완료
            - ACTIVE - 얼굴 스캔 완료 및 온보딩 완료
            - INACTIVE - 비활성화 및 삭제된 계정
            """)
    @PostMapping("/login")
    public ApiResponse<LoginResDTO> login(@Valid @RequestBody LoginReqDTO request) {
        return ApiResponse.onSuccess("로그인에 성공했습니다.", authService.login(request));
    }

    @Operation(summary = "Access token 재발급", description = "Refresh token을 검증한 뒤 새로운 access token을 발급합니다.")
    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResDTO> refresh(@Valid @RequestBody RefreshTokenReqDTO request) {
        return ApiResponse.onSuccess("Access token이 재발급되었습니다.", authService.refresh(request));
    }

    @Operation(summary = "로그아웃", description = "현재 access token 기준으로 사용자를 식별한 뒤 저장된 refresh token을 제거합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ApiResponse.onSuccess("로그아웃에 성공했습니다.");
    }

    @Operation(summary = "비밀번호 재설정 인증번호 발송", description = "가입 여부와 관계없이 동일한 성공 응답을 반환합니다.")
    @PostMapping("/password-reset/send-code")
    public ApiResponse<Void> sendPasswordResetCode(
            @Valid @RequestBody PasswordResetReqDTO.SendCodeDTO request,
            HttpSession session
    ) {
        passwordResetService.sendCode(request, session);
        return ApiResponse.onSuccess("가입된 이메일인 경우 인증번호를 전송했습니다.");
    }

    @Operation(summary = "비밀번호 재설정 인증번호 확인")
    @PostMapping("/password-reset/verify-code")
    public ApiResponse<Void> verifyPasswordResetCode(
            @Valid @RequestBody PasswordResetReqDTO.VerifyCodeDTO request,
            HttpSession session
    ) {
        passwordResetService.verifyCode(request, session);
        return ApiResponse.onSuccess("인증번호 확인에 성공했습니다.");
    }

    @Operation(summary = "비밀번호 재설정")
    @PostMapping("/password-reset/reset")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody PasswordResetReqDTO.ResetPasswordDTO request,
            HttpSession session
    ) {
        passwordResetService.resetPassword(request, session);
        return ApiResponse.onSuccess("비밀번호 재설정에 성공했습니다.");
    }
}
