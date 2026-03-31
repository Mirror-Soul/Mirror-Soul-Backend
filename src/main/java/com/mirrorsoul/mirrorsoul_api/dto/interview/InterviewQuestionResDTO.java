package com.mirrorsoul.mirrorsoul_api.dto.interview;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class InterviewQuestionResDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class questionListResDTO {
        private List<questionResDTO> questions;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class questionResDTO {
        private Long questionId;
        private String question;
    }
}
