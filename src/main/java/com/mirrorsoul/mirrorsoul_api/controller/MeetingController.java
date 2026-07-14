package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.match.MeetingReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.match.MeetingResDTO;
import com.mirrorsoul.mirrorsoul_api.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Meeting", description = "실제 사용자 간 만남 신청 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/match/meeting")
@RequiredArgsConstructor
public class MeetingController {
    private final MeetingService meetingService;

    @Operation(summary = "받은 만남 신청 목록 조회")
    @GetMapping("/requests")
    public ApiResponse<MeetingResDTO.RequestListDTO> getReceivedRequests(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess("받은 만남 신청 목록을 조회했습니다.",
                meetingService.getReceivedRequests(currentUser.getUuid()));
    }

    @Operation(summary = "만남 신청 보내기")
    @PostMapping("/requests")
    public ApiResponse<MeetingResDTO.CreatedDTO> createRequest(
            @Valid @RequestBody MeetingReqDTO.CreateDTO request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess("만남 신청을 보냈습니다.",
                meetingService.createRequest(currentUser.getUuid(), request));
    }

    @Operation(summary = "만남 신청 거절")
    @PostMapping("/requests/{request-id}/reject")
    public ApiResponse<MeetingResDTO.RespondedDTO> rejectRequest(
            @PathVariable("request-id") Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess("만남 신청을 거절했습니다.",
                meetingService.rejectRequest(currentUser.getUuid(), requestId));
    }

    @Operation(summary = "만남 신청 수락 및 채팅방 생성")
    @PostMapping("/requests/{request-id}/accept")
    public ApiResponse<MeetingResDTO.AcceptedDTO> acceptRequest(
            @PathVariable("request-id") Long requestId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess("만남 신청을 수락하고 채팅방을 생성했습니다.",
                meetingService.acceptRequest(currentUser.getUuid(), requestId));
    }
}
