package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.mail.EmailAuthConst;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final UserRepository userRepository;
    private final MailService mailService;

    private static final long EXPIRE_SECONDS = 180L;

    public void sendCode(Long userId, HttpSession session) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            throw new GeneralException(GeneralErrorCode.EMAIL_NOT_FOUND);
        }

        String code = createCode();
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(EXPIRE_SECONDS);

        mailService.sendVerificationCode(email, code);

        session.setAttribute(EmailAuthConst.EMAIL_AUTH_TARGET, email);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_CODE, code);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_EXPIRE_TIME, expireTime);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED, false);
        session.setMaxInactiveInterval((int) EXPIRE_SECONDS);
    }

    public JoinResDTO.verifyCodeResDTO verifyCode(Long userId, JoinReqDTO.verifyCodeReqDTO req, HttpSession session) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            throw new GeneralException(GeneralErrorCode.EMAIL_NOT_FOUND);
        }

        String savedEmail = (String) session.getAttribute(EmailAuthConst.EMAIL_AUTH_TARGET);
        String savedCode = (String) session.getAttribute(EmailAuthConst.EMAIL_AUTH_CODE);
        LocalDateTime expireTime = (LocalDateTime) session.getAttribute(EmailAuthConst.EMAIL_AUTH_EXPIRE_TIME);
        Boolean verified = (Boolean) session.getAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED);

        if (savedEmail == null || savedCode == null || expireTime == null) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_NOT_REQUESTED);
        }

        if (!email.equals(savedEmail)) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO, "인증 요청한 이메일 정보가 일치하지 않습니다.");
        }

        if (Boolean.TRUE.equals(verified)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        if (LocalDateTime.now().isAfter(expireTime)) {
            clearAuthSession(session);
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_EXPIRED);
        }

        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new GeneralException(GeneralErrorCode.MISSING_PARAMETER, "인증번호는 필수입니다.");
        }

        if (!savedCode.equals(req.getCode())) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_MISMATCH);
        }

        session.setAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED, true);

        return JoinResDTO.verifyCodeResDTO.builder()
                .verifySuccess(true)
                .build();
    }

    public boolean isVerified(HttpSession session, String email) {
        String savedEmail = (String) session.getAttribute(EmailAuthConst.EMAIL_AUTH_TARGET);
        Boolean verified = (Boolean) session.getAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED);

        return savedEmail != null
                && savedEmail.equals(email)
                && Boolean.TRUE.equals(verified);
    }

    public void clearAuthSession(HttpSession session) {
        session.removeAttribute(EmailAuthConst.EMAIL_AUTH_TARGET);
        session.removeAttribute(EmailAuthConst.EMAIL_AUTH_CODE);
        session.removeAttribute(EmailAuthConst.EMAIL_AUTH_EXPIRE_TIME);
        session.removeAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED);
    }

    private String createCode() {
        int number = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(number);
    }
}
