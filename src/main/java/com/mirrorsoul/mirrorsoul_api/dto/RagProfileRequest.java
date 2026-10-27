package com.mirrorsoul.mirrorsoul_api.dto;

import java.util.List;
import java.util.UUID;

public record RagProfileRequest(UUID userId, Long cloneId, String aiProfileId,
        Integer age, String gender, String mbti, String description,
        List<String> interests, List<String> interviewTopics,
        List<InterviewSample> interviewSamples, int keywordLimit, Long sourceRevision,
        com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.Trace trace) {
    public RagProfileRequest(UUID userId,Long cloneId,String aiProfileId,Integer age,String gender,String mbti,
            String description,List<String> interests,List<String> interviewTopics,List<InterviewSample> samples,int limit,Long revision) {
        this(userId,cloneId,aiProfileId,age,gender,mbti,description,interests,interviewTopics,samples,limit,revision,null);
    }
    public RagProfileRequest withTrace(com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.Trace trace) {
        return new RagProfileRequest(userId,cloneId,aiProfileId,age,gender,mbti,description,interests,interviewTopics,interviewSamples,keywordLimit,sourceRevision,trace);
    }
    public RagProfileRequest(UUID userId, Long cloneId, String aiProfileId, Integer age,
            String gender, String mbti, String description, List<String> interests,
            List<String> interviewTopics, List<InterviewSample> interviewSamples, int keywordLimit) {
        this(userId, cloneId, aiProfileId, age, gender, mbti, description, interests,
                interviewTopics, interviewSamples, keywordLimit, null);
    }
    public record InterviewSample(Long questionId, String questionCategory,
            String questionText, String transcript) {}
}
