package com.mirrorsoul.mirrorsoul_api.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class OnboardingResDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class checkDupNicknameResDTO {
        Boolean available;
    }
}
