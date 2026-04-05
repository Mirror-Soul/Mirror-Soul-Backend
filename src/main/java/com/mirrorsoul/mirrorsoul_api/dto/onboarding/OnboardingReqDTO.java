package com.mirrorsoul.mirrorsoul_api.dto.onboarding;

import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class OnboardingReqDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class personaReqDTO {

        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;

        @NotBlank(message = "시도명은 필수입니다.")
        private String sidoName;

        @NotBlank(message = "시군구명은 필수입니다.")
        private String sigunguName;

        @NotBlank(message = "읍면동명은 필수입니다.")
        private String eupmyeondongName;

        private String jobDescription;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class checkDupNicknameReqDTO {

        @NotBlank(message = "닉네임 입력은 필수입니다.")
        private String nickname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class personalityReqDTO {

        @NotNull(message = "MBTI는 필수입니다.")
        private MbtiType mbti;

        @NotNull(message = "IE 점수는 필수입니다.")
        private Integer ieScore;

        @NotNull(message = "NS 점수는 필수입니다.")
        private Integer nsScore;

        @NotNull(message = "FT 점수는 필수입니다.")
        private Integer ftScore;

        @NotNull(message = "PJ 점수는 필수입니다.")
        private Integer pjScore;

        @NotBlank(message = "자기소개는 필수입니다.")
        private String selfIntroduction;
    }
}
