package com.mirrorsoul.mirrorsoul_api.common.jwt;

import com.mirrorsoul.mirrorsoul_api.domain.User;

public interface TokenProvider {

    String createAccessToken(User user);

    String createRefreshToken(User user);
}
