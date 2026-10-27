package com.mirrorsoul.mirrorsoul_api.controller;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.member.*;
import com.mirrorsoul.mirrorsoul_api.service.AdminMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@Tag(name="Admin Members") @SecurityRequirement(name="bearerAuth")
@lombok.extern.slf4j.Slf4j
@RestController @RequestMapping("/admin/members") @RequiredArgsConstructor
public class AdminMemberController {
    private final AdminMemberService service;
    private final com.mirrorsoul.mirrorsoul_api.service.MemberDeletionService deletion;
    @GetMapping
    public ApiResponse<Page<MemberView>> list(@AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(required=false) String search, @RequestParam(required=false) String accountStatus,
            @RequestParam(required=false) UserStatus onboardingStage,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("회원 목록입니다.",service.list(actor.getId(),search,accountStatus,onboardingStage,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<MemberView> detail(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id) {
        return ApiResponse.onSuccess("회원 상세입니다.",service.detail(actor.getId(),id));
    }
    @GetMapping("/{id}/actions")
    public ApiResponse<Page<MemberActionResponse>> history(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("회원 조치 이력입니다.",service.history(actor.getId(),id,page,size));
    }
    @GetMapping("/{id}/deletion")
    public ApiResponse<MemberDeletionView> deletion(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id) {
        log.info("Admin member deletion viewed actorId={} memberId={}",actor.getId(),id);
        return ApiResponse.onSuccess("자료 삭제 진행 상태입니다.",deletion.status(id).orElse(null));
    }
    @PostMapping("/{id}/actions")
    public ApiResponse<MemberActionResponse> act(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id,
            @Valid @RequestBody MemberActionRequest request) {
        return ApiResponse.onSuccess("회원 조치를 처리했습니다.",service.act(actor.getId(),id,request));
    }
}
