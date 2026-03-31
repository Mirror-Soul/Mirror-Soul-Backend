package com.mirrorsoul.mirrorsoul_api.dto.login;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenReqDTO(
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
