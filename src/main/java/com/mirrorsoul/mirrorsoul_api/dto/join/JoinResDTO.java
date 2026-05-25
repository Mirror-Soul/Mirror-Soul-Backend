package com.mirrorsoul.mirrorsoul_api.dto.join;

import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class JoinResDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class basicProfileResDTO {
        UUID userUuid;
        String accessToken;
        String refreshToken;
        UserStatus userStatus;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class verifyCodeResDTO {
        Boolean verifySuccess;
    }

}
