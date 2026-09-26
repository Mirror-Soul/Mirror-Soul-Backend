package com.mirrorsoul.mirrorsoul_api.dto;

import java.util.List;
import java.util.UUID;

public record RagProfileRequest(UUID userId, Long cloneId, String aiProfileId,
        Integer age, String gender, String mbti, String description,
        List<String> interests, List<String> interviewTopics,
        List<InterviewSample> interviewSamples, int keywordLimit) {
    public record InterviewSample(Long questionId, String questionCategory,
            String questionText, String transcript) {}
}
