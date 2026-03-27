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
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "비활성화된 사용자입니다.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일 또는 비밀번호가 올바르지 않습니다.");
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
            throw new GeneralException(GeneralErrorCode.TOKEN_EXPIRED, "Refresh token이 만료되었습니다.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "유효하지 않은 refresh token입니다.");
        }

        Long userId = tokenProvider.getUserIdFromToken(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_TOKEN, "토큰에 해당하는 사용자를 찾을 수 없습니다."));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "비활성화된 사용자입니다.");
        }

        if (!Objects.equals(user.getRefreshToken(), request.refreshToken())) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "저장된 refresh token과 일치하지 않습니다.");
        }

        return new RefreshTokenResponse(tokenProvider.createAccessToken(user));
    }
}
