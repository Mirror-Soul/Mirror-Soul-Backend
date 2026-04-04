package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewQuestionResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewAnswerReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewAnswerResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.onboarding.OnboardingReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.onboarding.OnboardingResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewQuestionResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.visual.VisualReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.visual.VisualResDTO;
import com.mirrorsoul.mirrorsoul_api.service.InterviewService;
import com.mirrorsoul.mirrorsoul_api.service.OnboardingService;
import com.mirrorsoul.mirrorsoul_api.service.RegionService;
import com.mirrorsoul.mirrorsoul_api.service.VisualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Onboarding", description = "온보딩 API")
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final InterviewService interviewService;
    private final OnboardingService onboardingService;
    private final RegionService regionService;
    private final VisualService visualService;

    @Operation(summary = "닉네임,위치,직업 입력", description = """
        닉네임, 위치, 직업, 직업 상세정보 등록
        직업 분야 ENUM 리스트
        - IT_TECH : 기술 & IT
        - DESIGN : 디자인
        - PLANNING_STRATEGY : 기획 · 전략
        - MARKETING_PR : 마케팅 · PR
        - SALES_BUSINESS : 영업 · 비즈니스
        - HR_RECRUITING : 인사 · 채용
        - FINANCE_ACCOUNTING : 재무 · 회계
        - OPERATIONS_CS : 운영 · 고객지원
        - EDUCATION : 교육
        - MEDICAL_HEALTHCARE : 의료 · 헬스케어
        - MEDIA_CONTENT : 미디어 · 콘텐츠
        - LEGAL_PUBLIC : 법률 · 공공
        - MANUFACTURING_ENGINEERING : 제조 · 엔지니어링
        - STUDENT : 학생
        - FREELANCER : 프리랜서
        - ETC : 기타
        """)
    @PostMapping("/profile/{userId}")
    public ApiResponse<Void> postProfile(@Valid @RequestBody OnboardingReqDTO.personaReqDTO req,
                                         @PathVariable Long userId,
                                         @RequestParam Job job) {
        onboardingService.postProfile(req, userId, job);
        return ApiResponse.onSuccess("닉네임, 위치, 직업 등록완료");
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임 중복 확인 후 사용가능 여부 전송")
    @PostMapping("/profile/check-dup-nickname")
    public ApiResponse<Void> checkDupNickname(@RequestBody OnboardingReqDTO.checkDupNicknameReqDTO req) {

        if (onboardingService.checkDupNickname(req)){
            return ApiResponse.onSuccess("사용 가능한 닉네임입니다.");
        } else {return ApiResponse.onSuccess("사용 불가능한 중복된 닉네임입니다.");}
    }

    @GetMapping("/regions/sido")
    @Operation(summary = "지역 조회 - 시도", description = "시도 목록 조회")
    public ApiResponse<List<String>> getSidoList() {
        return ApiResponse.onSuccess("시도 목록 조회 성공", regionService.getSidoList());
    }

    @GetMapping("/regions/sigungu")
    @Operation(summary = "지역 조회 - 시군구", description = "해당 시도 내에 속한 시군구 목록조회")
    public ApiResponse<List<String>> getSigunguList(
            @RequestParam String sidoName
    ) {
        return ApiResponse.onSuccess("시군구 목록 조회 성공", regionService.getSigunguList(sidoName));
    }

    @GetMapping("/regions/eupmyeondong")
    @Operation(summary = "지역 조회 - 읍면동", description = "해당 시도,시군구 내에 속한 읍면동 목록조회")
    public ApiResponse<List<String>> getEupmyeondongList(
            @RequestParam String sidoName,
            @RequestParam String sigunguName
    ) {
        return ApiResponse.onSuccess("읍면동 목록 조회 성공",
                regionService.getEupmyeondongList(sidoName, sigunguName));
    }

    @Operation(summary = "온보딩 인터뷰 질문 전체 조회", description = "온보딩 단계에서 사용될 인터뷰 질문 전체를 조회합니다.")
    @GetMapping("/interview/questions")
    public ApiResponse<InterviewQuestionResDTO.questionListResDTO> getInterviewQuestions() {
        return ApiResponse.onSuccess("인터뷰 질문 조회에 성공했습니다.", interviewService.getQuestions());
    }

    @Operation(summary = "온보딩 인터뷰 답변 저장", description = "온보딩 단계에서 userId 기준으로 인터뷰 답변을 저장하거나 수정합니다.")
    @PostMapping("/interview/answers/{userId}")
    public ApiResponse<InterviewAnswerResDTO> saveInterviewAnswer(
            @PathVariable Long userId,
            @RequestBody InterviewAnswerReqDTO request
    ) {
        return ApiResponse.onSuccess("인터뷰 답변 저장에 성공했습니다.", interviewService.saveInterviewAnswer(userId, request));
    }

    @Operation(summary = "온보딩 얼굴 영상 파일 저장", description = "S3 업로드가 완료된 얼굴 영상 파일의 objectKey와 fileUrl을 userId 기준으로 저장합니다.")
    @PostMapping("/visual/{userId}")
    public ApiResponse<VisualResDTO> saveVisualFile(
            @PathVariable Long userId,
            @RequestBody VisualReqDTO request
    ) {
        return ApiResponse.onSuccess("얼굴 영상 파일 저장에 성공했습니다.", visualService.saveVisualFile(userId, request));
    }
}
