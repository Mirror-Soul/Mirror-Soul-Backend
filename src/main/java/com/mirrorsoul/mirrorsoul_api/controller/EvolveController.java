package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.service.EvolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evolve", description = "Evolve API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/evolve")
@RequiredArgsConstructor
public class EvolveController {

    private final EvolveService evolveService;

    @Operation(summary = "Twin sync rate", description = "Returns the current twin sync rate.")
    @GetMapping
    public ApiResponse<EvolveResDTO.twinSyncDTO> getTwinSync(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "Twin sync rate fetched successfully.",
                evolveService.twinSync(currentUser.getUuid())
        );
    }

    @Operation(summary = "Voice update recording line", description = "Returns a line to read for voice update recording.")
    @GetMapping("/voice")
    public ApiResponse<EvolveResDTO.speechLineDTO> startRecording(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "Voice update recording line fetched successfully.",
                evolveService.speechLine(currentUser.getUuid())
        );
    }

    @Operation(summary = "Complete voice update recording", description = "Creates a voice training job from an uploaded recording.")
    @PostMapping("/voice")
    public ApiResponse<EvolveResDTO.voiceUpdateJobDTO> completeRecording(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody EvolveReqDTO.VoiceUpdateCompleteDTO request
    ) {
        return ApiResponse.onSuccess(
                "Voice update job requested successfully.",
                evolveService.completeVoiceUpdate(currentUser.getUuid(), request)
        );
    }
}
