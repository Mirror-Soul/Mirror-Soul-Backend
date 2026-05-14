package com.mirrorsoul.mirrorsoul_api.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InterviewAnswerReqDTO(
        @NotNull(message = "interviewId is required.")
        Long interviewId,

        @NotBlank(message = "answerAudioObjectKey is required.")
        String answerAudioObjectKey,

        String answerText
) {
}
