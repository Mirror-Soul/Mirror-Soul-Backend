package com.mirrorsoul.mirrorsoul_api.dto.interview;

public record InterviewAnswerReqDTO(
        Long interviewId,
        String answerAudioUrl,
        String answerText
) {
}
