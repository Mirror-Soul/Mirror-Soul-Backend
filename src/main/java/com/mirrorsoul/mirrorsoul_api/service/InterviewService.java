package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.dto.interview.InterviewResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRepository;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewService {

    private final InterviewRepository interviewRepository;

    public InterviewResDTO.questionListResDTO getQuestions() {
        return InterviewResDTO.questionListResDTO.builder()
                .questions(interviewRepository.findAllByOrderByIdAsc().stream()
                        .map(interview -> InterviewResDTO.questionResDTO.builder()
                                .questionId(interview.getId())
                                .question(interview.getQuestion())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
