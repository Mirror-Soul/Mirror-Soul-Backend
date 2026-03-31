package com.mirrorsoul.mirrorsoul_api.dto.login;

public record LoginResDTO(
        String accessToken,
        String refreshToken,
        Long userId
) {
}
