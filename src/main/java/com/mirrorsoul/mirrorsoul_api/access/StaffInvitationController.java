package com.mirrorsoul.mirrorsoul_api.access;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController @RequiredArgsConstructor
public class StaffInvitationController {
 private final StaffService staff;
 @PostMapping("/staff-invitations/activate") public ApiResponse<AccessDtos.StaffView> activate(@AuthenticationPrincipal CustomUserDetails u,@Valid @RequestBody AccessDtos.Activate c) {return ApiResponse.onSuccess("직원 초대를 수락했습니다. 다시 로그인해 주세요.",staff.activate(u.getId(),c.token()));}
}
