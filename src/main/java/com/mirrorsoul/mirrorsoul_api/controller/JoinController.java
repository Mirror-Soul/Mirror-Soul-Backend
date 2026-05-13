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

    @Operation(summary = "1-3.기본 프로필",description = "이름,이메일,비밀번호,성별,생년월일 입력 및 이용 약관 동의 후 요청시, userUuid 반환 (여기서 발급된 userUuid를 통해 이후 온보딩 절차 진행)")
    @PostMapping("/basic-profile")
    ApiResponse<JoinResDTO.basicProfileResDTO> basicProfile(@Valid @RequestBody JoinReqDTO.basicProfileReqDTO req, HttpSession session) {
        return ApiResponse.onSuccess("회원 프로필 생성 완료", joinService.basicProfile(req, session));
    }

    @Operation(summary = "1-1.인증번호 전송", description = "해당 이메일로 인증번호 전송")
    @PostMapping("/send-code")
    ApiResponse<Void> sendCode(@RequestBody JoinReqDTO.sendCodeReqDTO req, HttpSession session) {
        emailAuthService.sendCode(req, session);
        return ApiResponse.onSuccess("인증코드 전송 완료");
    }

    @Operation(summary = "1-2.이메일 인증", description = "인증번호 일치 여부 확인 (개발용 마스터 코드는 123456)")
    @PostMapping("/verify-code")
    ApiResponse<JoinResDTO.verifyCodeResDTO> verifyCode(@RequestBody JoinReqDTO.verifyCodeReqDTO req, HttpSession session) {
        return ApiResponse.onSuccess("인증 코드 검증 완료",emailAuthService.verifyCode(req, session));
    }
}
