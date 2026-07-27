package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.push.PushDeviceReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.push.PushDeviceResDTO;
import com.mirrorsoul.mirrorsoul_api.service.PushDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Push Device", description = "푸시 알림 기기 등록 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/push/devices")
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @Operation(summary = "푸시 기기 등록 또는 토큰 갱신")
    @PutMapping
    public ApiResponse<PushDeviceResDTO.DeviceDTO> register(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody PushDeviceReqDTO.RegisterDTO request) {
        return ApiResponse.onSuccess(
                "푸시 기기를 등록했습니다.",
                pushDeviceService.register(currentUser.getUuid(), request)
        );
    }

    @Operation(summary = "푸시 기기 등록 해제")
    @DeleteMapping("/{installation-id}")
    public ApiResponse<Void> unregister(
            @PathVariable("installation-id") UUID installationId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        pushDeviceService.unregister(currentUser.getUuid(), installationId);
        return ApiResponse.onSuccess("푸시 기기 등록을 해제했습니다.");
    }
}
