package com.mirrorsoul.mirrorsoul_api.dto.login;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long userId
) {
}
