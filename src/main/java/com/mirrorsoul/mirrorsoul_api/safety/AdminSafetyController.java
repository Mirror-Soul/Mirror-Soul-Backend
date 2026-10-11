package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@Slf4j @RestController @RequestMapping("/admin/reports") @RequiredArgsConstructor
public class AdminSafetyController {
    private final SafetyReportService reports;
    private final SafetyDecisionService decisions;
    private final SafetyEvidenceService evidence;
    @GetMapping
    public ApiResponse<Page<ReportView>> list(@AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(required=false) Priority priority,@RequestParam(required=false) ReportState state,
            @RequestParam(required=false) Long assigneeId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("신고 목록입니다.",reports.list(actor.getId(),priority,state,assigneeId,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id) {
        return ApiResponse.onSuccess("신고 상세입니다.",reports.detail(actor.getId(),id));
    }
    @PatchMapping("/{id}/assignee")
    public ApiResponse<ReportView> assign(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id,@Valid @RequestBody Assign body) {
        return ApiResponse.onSuccess("담당자를 지정했습니다.",reports.assign(actor.getId(),id,body));
    }
    @GetMapping("/{id}/decisions")
    public ApiResponse<Page<DecisionView>> history(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("신고 처리 이력입니다.",reports.history(actor.getId(),id,page,size));
    }
    @PostMapping("/{id}/decisions")
    public ApiResponse<DecisionView> decide(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id,@Valid @RequestBody Decide body) {
        try { return ApiResponse.onSuccess("신고 처리 결과를 기록했습니다.",decisions.decide(actor.getId(),id,body)); }
        catch(RuntimeException e) {
            log.warn("Safety decision failed reportId={} actorId={} exceptionType={}",id,actor.getId(),e.getClass().getSimpleName());throw e;
        }
    }
    @PostMapping("/{id}/evidence/access")
    public ApiResponse<Evidence> access(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id,@Valid @RequestBody EvidenceAccess body) {
        return ApiResponse.onSuccess("제한된 신고 증거입니다.",evidence.access(actor.getId(),id,body.reason()));
    }
    @GetMapping("/{id}/evidence/audits")
    public ApiResponse<Page<AuditView>> audits(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable Long id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("증거 접근 감사 기록입니다.",evidence.audits(actor.getId(),id,page,size));
    }
}
