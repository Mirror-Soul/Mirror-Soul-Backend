package com.mirrorsoul.mirrorsoul_api.quality;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/admin") @RequiredArgsConstructor
public class AdminQualityController {
    private final TrainingReadService training;
    private final TrainingControlService control;
    private final CallDiagnosticsReadService calls;
    @GetMapping("/clone-training-jobs")
    public ApiResponse<Page<TrainingDtos.Job>> jobs(@RequestParam(required=false) TrainingId.Kind kind,
            @RequestParam(required=false) Long memberId,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) OffsetDateTime from,@RequestParam(required=false) OffsetDateTime to,
            @RequestParam(defaultValue="false") boolean unresolvedOnly,@RequestParam(required=false) OffsetDateTime stalledBefore) {
        var window=new com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow(from,to);
        return ApiResponse.onSuccess("트윈 학습 작업입니다.",training.list(new TrainingJobFilter(kind,memberId,status,window,
                unresolvedOnly,com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow.local(stalledBefore)),page,size));
    }
    @PostMapping("/clone-training-jobs/{id}/retry")
    public ApiResponse<TrainingDtos.RetryResult> retry(@AuthenticationPrincipal CustomUserDetails actor,
            @PathVariable String id,@Valid @RequestBody TrainingDtos.Retry request) {
        return ApiResponse.onSuccess("학습 재시도를 예약했습니다.",control.retry(actor.getId(),id,request));
    }
    @GetMapping("/calls/{id}/diagnostics")
    public ApiResponse<CallQualityDtos.Diagnostics> diagnostics(@PathVariable Long id) {
        return ApiResponse.onSuccess("통화 품질 진단입니다.",calls.get(id));
    }
    @GetMapping("/calls/diagnostics/summary")
    public ApiResponse<CallQualityDtos.Window> distribution(@RequestParam OffsetDateTime from,@RequestParam OffsetDateTime to,
            @RequestParam(required=false) Long cloneId,@RequestParam(required=false) Long cloneVersion,
            @RequestParam(required=false) CallMediaType mediaType) {
        return ApiResponse.onSuccess("측정 기간의 지연 분포입니다.",calls.distribution(from,to,null,cloneId,cloneVersion,mediaType));
    }
}
