package com.mirrorsoul.mirrorsoul_api.dto.profile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.SpeechSpeed;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ProfileResDTO {

    public record MyProfileDetailDTO(
            UUID userUuid,
            String email,
            String name,
            Integer age,
            String profileImageUrl,
            BigDecimal syncRate,
            RegionDTO region,
            Job job,
            String jobDescription,
            boolean jobCertificationSubmitted,
            String selfIntroduction,
            MbtiType mbti,
            MbtiAxisScoresDTO mbtiAxisScores,
            List<String> personalityTags,
            VoicePreviewDTO voicePreview,
            boolean matchingEnabled
    ) {
    }

    public record RegionDTO(
            String sidoName,
            String sigunguName
    ) {
    }

    public record MbtiAxisScoresDTO(
            Integer ieScore,
            Integer nsScore,
            Integer ftScore,
            Integer pjScore
    ) {
    }

    public record VoicePreviewDTO(
            String audioUrl,
            String contentType,
            Integer durationMs
    ) {
    }

    public record CloneStatusDTO(
            String cloneStatus,
            CloneStatusComponentsDTO components,
            BigDecimal syncRate
    ) {
    }

    public record CloneStatusComponentsDTO(
            CloneStatusComponentDTO voice,
            CloneStatusComponentDTO face,
            CloneStatusComponentDTO personality,
            CloneStatusComponentDTO profileSummary
    ) {
    }

    public record CloneStatusComponentDTO(
            String status,
            LocalDateTime updatedAt,
            String errorCode
    ) {
    }

    public record ProfileImageDTO(
            String profileImageUrl
    ) {
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class myProfileDTO {
        String name;
        String email;
        String profileImageUrl;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class timeStatusDTO {
        Integer remainingTalkTime;
        Integer hours;
        Integer minutes;
        Integer seconds;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class audioSettingsDTO {
        Integer opponentVoiceVolume;
        SpeechSpeed opponentSpeechSpeed;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class accountInfoDTO {
        String name;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class alarmSettingDTO {
        Boolean missedCallNotificationEnabled;
        Boolean lowTimeNotificationEnabled;
    }
}
