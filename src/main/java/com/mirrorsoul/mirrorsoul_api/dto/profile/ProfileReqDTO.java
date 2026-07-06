package com.mirrorsoul.mirrorsoul_api.dto.profile;

import com.mirrorsoul.mirrorsoul_api.domain.enums.SpeechSpeed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class ProfileReqDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class buyTimeReqDTO {
        @NotNull
        @Min(1)
        Integer buyTime;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class audioSettingsReqDTO {
        @NotNull
        @Min(0)
        @Max(100)
        Integer opponentVoiceVolume;

        @NotNull
        SpeechSpeed opponentSpeechSpeed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class modifyNicknameReqDTO {
        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class alarmSettingReqDTO {
        @NotNull
        Boolean missedCallNotificationEnabled;

        @NotNull
        Boolean lowTimeNotificationEnabled;
    }
}
