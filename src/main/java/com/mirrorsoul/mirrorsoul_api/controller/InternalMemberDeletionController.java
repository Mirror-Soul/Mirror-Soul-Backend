package com.mirrorsoul.mirrorsoul_api.controller;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob.State;
import com.mirrorsoul.mirrorsoul_api.dto.member.MemberDeletionView;
import com.mirrorsoul.mirrorsoul_api.service.MemberDeletionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/ai/member-deletions") @RequiredArgsConstructor
public class InternalMemberDeletionController {
    private final MemberDeletionService service;
    public record Result(@NotNull Long memberId,@Min(1) int attempt,@NotNull State state) {}
    @GetMapping
    public ApiResponse<Page<MemberDeletionView>> available(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ApiResponse.onSuccess("삭제 작업 목록입니다.",service.available(page,size));
    }
    @PostMapping("/{id}/claim")
    public ApiResponse<MemberDeletionView> claim(@PathVariable Long id) {
        return ApiResponse.onSuccess("삭제 작업을 할당했습니다.",service.claim(id));
    }
    @PostMapping("/{id}/result")
    public ApiResponse<MemberDeletionView> report(@PathVariable Long id,@Valid @RequestBody Result result) {
        return ApiResponse.onSuccess("삭제 결과를 기록했습니다.",service.report(id,result.memberId(),result.attempt(),result.state()));
    }
}
