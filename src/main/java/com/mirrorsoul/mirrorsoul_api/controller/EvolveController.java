package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewAnswerReqDTO;
import com.mirrorsoul.mirrorsoul_api.service.EvolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Evolve", description = "Evolve 관련 API")
@RestController
@RequestMapping("/evolve")
@RequiredArgsConstructor
public class EvolveController {

    private final EvolveService evolveService;

    @Operation(summary = "트윈 완성도 조회 api", description = "트윈 완성도를 %(퍼센트) 단위로 조회합니다.")
    @GetMapping
    public ApiResponse<EvolveResDTO.twinSyncDTO> getTwinSync(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess("트윈 완성도 조회에 성공했습니다.", evolveService.twinSync(currentUser.getUuid()));
    }

    @Operation(summary = "목소리 업데이트 - 녹음 api", description = "녹음을 위한 문장을 생성합니다.")
    @GetMapping("/voice")
    public ApiResponse<EvolveResDTO.speechLineDTO> startRecording(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess("녹음을 위한 문장 생성에 성공했습니다.", evolveService.speechLine(currentUser.getUuid()));
    }

    @Operation(summary = "목소리 업데이트 - 녹음완료 api", description = "녹음 완료한 음성파일을 저장합니다.")
    @PostMapping("/voice")
    public ApiResponse<Void> completeRecording(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody InterviewAnswerReqDTO request
    ) {
        return ApiResponse.onSuccess("ai서버와 연결 완료 후 개발 완료 예정", null);
    }
}
