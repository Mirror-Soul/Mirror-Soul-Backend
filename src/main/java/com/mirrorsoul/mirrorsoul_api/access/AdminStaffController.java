package com.mirrorsoul.mirrorsoul_api.access;
import com.mirrorsoul.mirrorsoul_api.access.AccessDtos.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
@RestController @RequestMapping("/admin") @RequiredArgsConstructor
public class AdminStaffController {
 private final StaffService staff;
 private final AdminAuditService audit;
 @GetMapping("/me") public ApiResponse<StaffView> me(@AuthenticationPrincipal CustomUserDetails u) {return ApiResponse.onSuccess("현재 직원 권한입니다.",staff.me(u.getId()));}
 @GetMapping("/staff") public ApiResponse<Page<StaffView>> list(@AuthenticationPrincipal CustomUserDetails u,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.onSuccess("직원 목록입니다.",staff.list(u.getId(),page,size));}
 @PostMapping("/reauth") public ApiResponse<ReauthView> reauth(@AuthenticationPrincipal CustomUserDetails u,@Valid @RequestBody Reauthenticate c) {return ApiResponse.onSuccess("5분간 유효한 일회용 재인증입니다.",staff.reauthenticate(u.getId(),c));}
 @PostMapping("/staff/invitations") public ApiResponse<InviteView> invite(@AuthenticationPrincipal CustomUserDetails u,@RequestHeader(value="X-Admin-Reauth",required=false) String token,@Valid @RequestBody Invite c) {return ApiResponse.onSuccess("직원 초대를 생성했습니다.",staff.invite(u.getId(),c,token));}
 @PatchMapping("/staff/{id}/permissions") public ApiResponse<StaffView> grant(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestHeader(value="X-Admin-Reauth",required=false) String token,@Valid @RequestBody Grant c) {return ApiResponse.onSuccess("직원 권한을 변경했습니다.",staff.grant(u.getId(),id,c,token));}
 @PostMapping("/staff/{id}/revoke") public ApiResponse<StaffView> revoke(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestHeader(value="X-Admin-Reauth",required=false) String token,@Valid @RequestBody Revoke c) {return ApiResponse.onSuccess("직원 권한과 기존 세션을 회수했습니다.",staff.revoke(u.getId(),id,c,token));}
 @GetMapping("/audit-events") public ApiResponse<Page<AuditView>> audits(@AuthenticationPrincipal CustomUserDetails u,@RequestParam(required=false) Long staffId,@RequestParam(required=false) String result,@RequestParam(required=false) LocalDateTime from,@RequestParam(required=false) LocalDateTime to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.onSuccess("관리자 감사 기록입니다.",audit.list(u.getId(),staffId,result,from,to,page,size));}
}
