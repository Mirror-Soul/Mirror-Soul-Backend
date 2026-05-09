package com.mirrorsoul.mirrorsoul_api.dto.login;

import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;

public record LoginResDTO(
        String accessToken,
        String refreshToken,
        Long userId,
        UserStatus userStatus
) {
}
