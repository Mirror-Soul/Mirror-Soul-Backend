package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.chat.ChatResDTO;
import com.mirrorsoul.mirrorsoul_api.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat", description = "1:1 텍스트 채팅 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @Operation(summary = "내 채팅방 목록 조회")
    @GetMapping("/rooms")
    public ApiResponse<ChatResDTO.RoomListDTO> getRooms(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess(
                "채팅방 목록을 조회했습니다.",
                chatService.getRooms(currentUser.getUuid())
        );
    }

    @Operation(summary = "채팅 메시지 내역 조회")
    @GetMapping("/rooms/{room-id}/messages")
    public ApiResponse<ChatResDTO.MessageListDTO> getMessages(
            @PathVariable("room-id") Long roomId,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess(
                "채팅 메시지를 조회했습니다.",
                chatService.getMessages(currentUser.getUuid(), roomId, beforeMessageId, size)
        );
    }

    @Operation(summary = "텍스트 메시지 전송")
    @PostMapping("/rooms/{room-id}/messages")
    public ApiResponse<ChatResDTO.MessageDTO> sendMessage(
            @PathVariable("room-id") Long roomId,
            @Valid @RequestBody ChatReqDTO.SendMessageDTO request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess(
                "메시지를 전송했습니다.",
                chatService.sendMessage(currentUser.getUuid(), roomId, request)
        );
    }

    @Operation(summary = "채팅 메시지 읽음 처리")
    @PatchMapping("/rooms/{room-id}/read")
    public ApiResponse<ChatResDTO.ReadResultDTO> readMessages(
            @PathVariable("room-id") Long roomId,
            @Valid @RequestBody ChatReqDTO.ReadMessageDTO request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ApiResponse.onSuccess(
                "읽음 상태를 반영했습니다.",
                chatService.readMessages(currentUser.getUuid(), roomId, request)
        );
    }
}
