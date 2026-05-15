package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Interview;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewQuestionResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewAnswerReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewAnswerResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.FORBIDDEN;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

    public InterviewQuestionResDTO.questionListResDTO getQuestions() {
        return InterviewQuestionResDTO.questionListResDTO.builder()
                .questions(interviewRepository.findAllByOrderByIdAsc().stream()
                        .map(interview -> InterviewQuestionResDTO.questionResDTO.builder()
                                .questionId(interview.getId())
                                .question(interview.getQuestion())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public InterviewAnswerResDTO saveInterviewAnswer(UUID userUuid, InterviewAnswerReqDTO request) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND, "User not found."));

        if (!UserStatus.ONBOARD_C.equals(user.getStatus())) {
            throw new GeneralException(FORBIDDEN, "ONBOARD_C 상태의 사용자만 음성 인터뷰 답변을 저장할 수 있습니다.");
        }

        Interview interview = interviewRepository.findById(request.interviewId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "Interview not found."));

        String answerAudioUrl = fileService.verifyInterviewAudioAndBuildFileUrl(userUuid, request.answerAudioObjectKey());

        InterviewRecord interviewRecord = interviewRecordRepository.findByUser_IdAndInterview_Id(user.getId(), request.interviewId())
                .map(record -> {
                    record.updateAnswer(answerAudioUrl, request.answerText());
                    return record;
                })
                .orElseGet(() -> interviewRecordRepository.save(
                        InterviewRecord.create(user, interview, answerAudioUrl, request.answerText())
                ));

        long totalInterviewCount = interviewRepository.count();
        long answeredInterviewCount = interviewRecordRepository.countByUser_Id(user.getId());

        if (answeredInterviewCount >= totalInterviewCount) {
            user.setStatus(UserStatus.ONBOARD_D);
        }

        return new InterviewAnswerResDTO(
                interviewRecord.getId(),
                interview.getId(),
                true
        );
    }
}
