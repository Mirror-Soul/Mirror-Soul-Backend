package com.mirrorsoul.mirrorsoul_api.jobreview;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-verifications")
public class JobReviewExtensionController {
    private final JobReviewExtensionService service;
    @GetMapping("/history")
    public ApiResponse<Page<JobReviewExtensionService.History>> history(
            @RequestParam(required=false) JobVerificationRequestStatus status,
            @RequestParam(required=false) Long reviewerId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required=false) Long requestId, @RequestParam(required=false) Long version,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("심사 처리 이력입니다.", service.history(status,reviewerId,from,to,requestId,version,page,size));
    }
    @GetMapping("/{requestId}/lifecycle")
    public ApiResponse<JobReviewExtensionService.Lifecycle> lifecycle(@PathVariable Long requestId) {
        return ApiResponse.onSuccess("서류 보관 및 통지 상태입니다.", service.lifecycle(requestId));
    }
    public record Retry(@NotNull Long version, boolean possibleDuplicateAcknowledged) {}
    @PostMapping("/{requestId}/notification/retry")
    public ApiResponse<JobReviewExtensionService.Lifecycle> retry(@PathVariable Long requestId, @Valid @RequestBody Retry body) {
        return ApiResponse.onSuccess("통지 재시도를 예약했습니다.", service.retryNotification(requestId,body.version(),body.possibleDuplicateAcknowledged()));
    }
}
