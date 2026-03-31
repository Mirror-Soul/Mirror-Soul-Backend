package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewQuestionResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewAnswerReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewAnswerResDTO;
import com.mirrorsoul.mirrorsoul_api.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Onboarding", description = "온보딩 API")
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final InterviewService interviewService;

    @Operation(summary = "온보딩 인터뷰 질문 전체 조회", description = "온보딩 단계에서 사용할 인터뷰 질문 전체를 조회합니다.")
    @GetMapping("/interview/questions")
    public ApiResponse<InterviewQuestionResDTO.questionListResDTO> getInterviewQuestions() {
        return ApiResponse.onSuccess("인터뷰 질문 조회에 성공했습니다.", interviewService.getQuestions());
    }

    @Operation(summary = "온보딩 인터뷰 답변 저장", description = "로그인 전 온보딩 단계에서 userId 기준으로 인터뷰 답변을 저장하거나 수정합니다.")
    @PostMapping("/interview/answers/{userId}")
    public ApiResponse<InterviewAnswerResDTO> saveInterviewAnswer(
            @PathVariable Long userId,
            @RequestBody InterviewAnswerReqDTO request
    ) {
        return ApiResponse.onSuccess("인터뷰 답변 저장에 성공했습니다.", interviewService.saveInterviewAnswer(userId, request));
    }
}
