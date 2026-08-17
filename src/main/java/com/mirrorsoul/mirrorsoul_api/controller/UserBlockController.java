package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Block", description = "사용자 차단 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class UserBlockController {

    private final UserBlockService userBlockService;

    @Operation(summary = "사용자 차단")
    @PostMapping("/{target-user-uuid}")
    public ApiResponse<Void> block(
            @PathVariable("target-user-uuid") UUID targetUserUuid,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        userBlockService.block(currentUser.getUuid(), targetUserUuid);
        return ApiResponse.onSuccess("사용자를 차단했습니다.");
    }

    @Operation(summary = "사용자 차단 해제")
    @DeleteMapping("/{target-user-uuid}")
    public ApiResponse<Void> unblock(
            @PathVariable("target-user-uuid") UUID targetUserUuid,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        userBlockService.unblock(currentUser.getUuid(), targetUserUuid);
        return ApiResponse.onSuccess("사용자 차단을 해제했습니다.");
    }
}
