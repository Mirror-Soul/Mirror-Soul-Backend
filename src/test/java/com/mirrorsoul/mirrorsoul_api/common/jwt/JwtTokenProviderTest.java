package com.mirrorsoul.mirrorsoul_api.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void getUserIdFromAccessTokenAllowExpiredReturnsSubjectFromExpiredAccessToken() {
        JwtProperties jwtProperties = new JwtProperties(SECRET, -1L, 1209600L);
        JwtTokenProvider tokenProvider = new JwtTokenProvider(jwtProperties);
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("password")
                .status(UserStatus.ACTIVE)
                .build();

        String expiredAccessToken = tokenProvider.createAccessToken(user);

        assertThat(tokenProvider.getUserIdFromAccessTokenAllowExpired(expiredAccessToken)).isEqualTo(1L);
    }
}
