package com.mirrorsoul.mirrorsoul_api.dto.onboarding;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
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

        @NotBlank(message = "시도명(지역)은 필수입니다.")
        private String sidoName;

        @NotBlank(message = "시군구명(지역)은 필수입니다.")
        private String sigunguName;

        @NotBlank(message = "읍명동명(지역)은 필수입니다.")
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
}
