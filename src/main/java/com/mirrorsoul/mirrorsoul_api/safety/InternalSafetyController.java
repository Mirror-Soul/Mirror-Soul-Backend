package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/internal/ai/safety") @RequiredArgsConstructor
public class InternalSafetyController {
    private final SafetyMemberService members;
    private final SafetyEvidenceService evidence;
    @GetMapping("/users/{uuid}/permissions")
    public ApiResponse<Permission> permissions(@PathVariable UUID uuid) {
        return ApiResponse.onSuccess("AI 작업 허용 상태입니다.",members.permission(uuid));
    }
    @PostMapping("/calls/{id}/diagnostics")
    public ApiResponse<DiagnosticView> diagnostic(@PathVariable Long id,@Valid @RequestBody Diagnostic body) {
        return ApiResponse.onSuccess("통화 진단 이벤트를 기록했습니다.",evidence.diagnostic(id,body));
    }
}
