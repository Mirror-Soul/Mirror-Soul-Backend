package com.mirrorsoul.mirrorsoul_api.quality;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/ai") @RequiredArgsConstructor
public class InternalQualityController {
    private final TrainingControlService training;
    private final CallQualityService calls;
    @PostMapping("/clone-training-jobs/{id}/events")
    public ApiResponse<TrainingDtos.Received> training(@PathVariable String id,@Valid @RequestBody TrainingDtos.Lifecycle event) {
        return ApiResponse.onSuccess("학습 상태 이벤트를 처리했습니다.",training.event(id,event));
    }
    @PostMapping("/calls/{id}/diagnostics")
    public ApiResponse<CallQualityDtos.Received> call(@PathVariable Long id,@Valid @RequestBody CallQualityDtos.Event event) {
        return ApiResponse.onSuccess("통화 품질 이벤트를 처리했습니다.",calls.receive(id,event));
    }
}
