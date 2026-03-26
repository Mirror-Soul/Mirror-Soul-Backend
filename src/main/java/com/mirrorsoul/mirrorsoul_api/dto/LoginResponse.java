package com.mirrorsoul.mirrorsoul_api.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long userId
) {
}
