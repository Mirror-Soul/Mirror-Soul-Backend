package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.jwt.TokenProvider;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.LoginRequest;
import com.mirrorsoul.mirrorsoul_api.dto.LoginResponse;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
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

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "비활성화된 사용자입니다.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return new LoginResponse(
                tokenProvider.createAccessToken(user),
                tokenProvider.createRefreshToken(user),
                user.getId()
        );
    }
}
