package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor
public class MemberSafetyController {
    private final SafetyReportService reports;
    private final SafetyMemberService members;
    @PostMapping("/reports")
    public ApiResponse<ReportView> submit(@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody Submit body) {
        return ApiResponse.onSuccess("신고를 접수했습니다.",reports.submit(user.getId(),body));
    }
    @GetMapping("/reports")
    public ApiResponse<Page<ReportView>> mine(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("내 신고 목록입니다.",reports.mine(user.getId(),page,size));
    }
    @GetMapping("/reports/{id}")
    public ApiResponse<ReportView> mine(@AuthenticationPrincipal CustomUserDetails user,@PathVariable Long id) {
        return ApiResponse.onSuccess("신고 접수 상태입니다.",reports.mine(user.getId(),id));
    }
    @PostMapping("/reports/{id}/appeals")
    public ApiResponse<AppealView> appeal(@AuthenticationPrincipal CustomUserDetails user,@PathVariable Long id,@Valid @RequestBody Appeal body) {
        return ApiResponse.onSuccess("이의 제기를 접수했습니다.",members.appeal(user.getId(),id,body));
    }
    @GetMapping("/safety/appeals")
    public ApiResponse<Page<AppealView>> appeals(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("내 이의 제기 목록입니다.",members.appeals(user.getId(),page,size));
    }
    @GetMapping("/safety/notices")
    public ApiResponse<Page<NoticeView>> notices(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("신고 및 제재 결과 안내입니다.",members.notices(user.getId(),page,size));
    }
    @PostMapping("/safety/notices/{id}/read")
    public ApiResponse<NoticeView> read(@AuthenticationPrincipal CustomUserDetails user,@PathVariable Long id) {
        return ApiResponse.onSuccess("안내를 확인했습니다.",members.readNotice(user.getId(),id));
    }
    @GetMapping("/safety/sanctions")
    public ApiResponse<List<SanctionView>> sanctions(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("내 이용 제한 내역입니다.",members.sanctions(user.getId(),page,size));
    }
    @GetMapping("/safety/status")
    public ApiResponse<Permission> status(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.onSuccess("서비스 이용 허용 상태입니다.",members.permission(user.getUuid()));
    }
}
