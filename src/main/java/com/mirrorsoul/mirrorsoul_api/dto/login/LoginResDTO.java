package com.mirrorsoul.mirrorsoul_api.dto.login;

import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import java.util.UUID;

public record LoginResDTO(
        String accessToken,
        String refreshToken,
        UUID userUuid,
        UserStatus userStatus
) {
}
