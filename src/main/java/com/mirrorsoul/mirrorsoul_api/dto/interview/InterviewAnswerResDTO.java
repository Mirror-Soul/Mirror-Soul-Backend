package com.mirrorsoul.mirrorsoul_api.dto.interview;

public record InterviewAnswerResDTO(
        Long recordId,
        Long interviewId,
        Boolean saved
) {
}
