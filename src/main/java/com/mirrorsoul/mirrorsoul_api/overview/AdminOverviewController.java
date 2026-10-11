package com.mirrorsoul.mirrorsoul_api.overview;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AdminOverviewController {
    private final AdminOverviewService service;
    @GetMapping("/admin/overview")
    public ApiResponse<OverviewDtos.Overview> get(@AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(required=false) OffsetDateTime from,@RequestParam(required=false) OffsetDateTime to) {
        return ApiResponse.onSuccess("운영 홈 집계입니다.",service.get(actor.getId(),from,to));
    }
}
