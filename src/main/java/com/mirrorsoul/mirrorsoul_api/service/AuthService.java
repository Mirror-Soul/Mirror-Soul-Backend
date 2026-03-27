package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.jwt.TokenProvider;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.LoginRequest;
import com.mirrorsoul.mirrorsoul_api.dto.LoginResponse;
import com.mirrorsoul.mirrorsoul_api.dto.RefreshTokenRequest;
import com.mirrorsoul.mirrorsoul_api.dto.RefreshTokenResponse;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_LOGIN, "Invalid email or password."));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "Inactive user.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new GeneralException(GeneralErrorCode.INVALID_LOGIN, "Invalid email or password.");
        }

        String accessToken = tokenProvider.createAccessToken(user);
        String refreshToken = tokenProvider.createRefreshToken(user);
        user.updateRefreshToken(refreshToken);

        return new LoginResponse(accessToken, refreshToken, user.getId());
    }

    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        try {
            tokenProvider.validateRefreshToken(request.refreshToken());
        } catch (ExpiredJwtException e) {
            throw new GeneralException(GeneralErrorCode.TOKEN_EXPIRED, "Refresh token has expired.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "Invalid refresh token.");
        }

        Long userId = tokenProvider.getUserIdFromToken(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_TOKEN, "User for token not found."));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "Inactive user.");
        }

        if (!Objects.equals(user.getRefreshToken(), request.refreshToken())) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "Refresh token does not match stored value.");
        }

        return new RefreshTokenResponse(tokenProvider.createAccessToken(user));
    }

    @Transactional
    public void logout(String authorizationHeader) {
        String accessToken = extractBearerToken(authorizationHeader);

        try {
            tokenProvider.validateAccessToken(accessToken);
        } catch (ExpiredJwtException e) {
            throw new GeneralException(GeneralErrorCode.TOKEN_EXPIRED, "Access token has expired.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "Invalid access token.");
        }

        Long userId = tokenProvider.getUserIdFromToken(accessToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_TOKEN, "User for token not found."));

        user.clearRefreshToken();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "Authorization header is required.");
        }

        String bearerPrefix = "Bearer ";
        if (!authorizationHeader.startsWith(bearerPrefix)) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "Authorization header must use Bearer token.");
        }

        String accessToken = authorizationHeader.substring(bearerPrefix.length()).trim();
        if (accessToken.isEmpty()) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "Access token is empty.");
        }

        return accessToken;
    }
}
