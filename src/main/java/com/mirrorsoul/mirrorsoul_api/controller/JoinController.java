package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinResDTO;
import com.mirrorsoul.mirrorsoul_api.service.EmailAuthService;
import com.mirrorsoul.mirrorsoul_api.service.JoinService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Join", description = "회원가입 API")
@RestController
@RequestMapping("/join")
@RequiredArgsConstructor
public class JoinController {

    private final JoinService joinService;
    private final EmailAuthService emailAuthService;

    @Operation(summary = "1.기본 프로필",description = "이름,이메일,비밀번호,성별,생년월일 입력후 요청시, userId 반환 (여기서 발급된 userId를 통해 이후 회원가입 절차 진행)")
    @PostMapping("/basic-profile")
    ApiResponse<JoinResDTO.basicProfileResDTO> basicProfile(@Valid @RequestBody JoinReqDTO.basicProfileReqDTO req) {
        return ApiResponse.onSuccess("회원 프로필 생성 완료", joinService.basicProfile(req));
    }

    @Operation(summary = "2-1.인증번호 전송", description = "해당 이메일로 인증번호 전송")
    @PostMapping("/send-code/{userId}")
    ApiResponse<Void> sendCode(@PathVariable Long userId, HttpSession session) {
        emailAuthService.sendCode(userId, session);
        return ApiResponse.onSuccess("인증코드 전송 완료");
    }

    @Operation(summary = "2-2.이메일 인증", description = "인증번호 일치 여부 확인")
    @PostMapping("/verify-code/{userId}")
    ApiResponse<JoinResDTO.verifyCodeResDTO> verifyCode(@PathVariable Long userId, @RequestBody JoinReqDTO.verifyCodeReqDTO req, HttpSession session) {
        return ApiResponse.onSuccess("인증 코드 검증 완료",emailAuthService.verifyCode(userId, req, session));
    }
}
