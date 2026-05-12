package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.mail.EmailAuthConst;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class JoinService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public JoinResDTO.basicProfileResDTO basicProfile(JoinReqDTO.basicProfileReqDTO req, HttpSession session){

        String savedEmail = (String) session.getAttribute(EmailAuthConst.EMAIL_AUTH_TARGET);
        Boolean verified = (Boolean) session.getAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED);

        if (savedEmail == null) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_NOT_REQUESTED);
        }

        if (!savedEmail.equals(req.getEmail())) {
            throw new GeneralException(
                    GeneralErrorCode.MISSING_AUTH_INFO,
                    "인증 완료된 이메일과 가입 요청 이메일이 일치하지 않습니다."
            );
        }

        if (!Boolean.TRUE.equals(verified)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (!req.getTermsAgreed()) {
            throw new GeneralException(GeneralErrorCode.NOT_AGREED_TERM);
        }

        if (userRepository.existsByEmail(req.getEmail())) {

            throw new GeneralException(GeneralErrorCode.DUPLICATE_EMAIL);

        }
        String encodedPassword = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(encodedPassword)
                .gender(req.getGender())
                .birthDate(req.getBirthDate())
                .status(UserStatus.ONBOARD_A)
                .build();

        userRepository.save(user);

        return JoinResDTO.basicProfileResDTO.builder()
                .userId(user.getId())
                .build();
    }
}
