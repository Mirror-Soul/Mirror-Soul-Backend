package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Device(예시)",
        description = """
        기기 관련 API - by 남성현
        - 기기 생성(예시)
        - 기기 삭제(예시)
        - 기기 정보 수정(예시)
        """
)

@RestController
public class ExampleController {

    /**
     * Produce a success ApiResponse containing a greeting message.
     *
     * @return an ApiResponse whose payload is the string "Hello World"
     */
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "COMMON2000", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "USER_4001", description = "이미 중복되는 email")
    })
    @GetMapping("/test")
    @Operation(summary = "회원가입 API", description = "아이디(이메일), 비밀번호, 닉네임, 전화번호 입력")
    public ApiResponse<Void> test() {
        return ApiResponse.onSuccess("Hello World");
    }
}
