package com.mirrorsoul.mirrorsoul_api.dto.profile;

import com.mirrorsoul.mirrorsoul_api.domain.enums.SpeechSpeed;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ProfileResDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class myProfileDTO {
        String name;
        String email;
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
