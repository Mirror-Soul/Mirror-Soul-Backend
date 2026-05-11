package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.mail.EmailAuthConst;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinResDTO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final MailService mailService;
    private static final String DEV_MASTER_CODE = "123456";
    private static final int VERIFY_MAX_COUNT = 5;

    private static final long EXPIRE_SECONDS = 180L;

    public void sendCode(JoinReqDTO.sendCodeReqDTO dto, HttpSession session) {

        String email = dto.getEmail();

        String code = createCode();
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(EXPIRE_SECONDS);

        mailService.sendVerificationCode(email, code);

        session.setAttribute(EmailAuthConst.EMAIL_AUTH_TARGET, email);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_CODE, code);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_EXPIRE_TIME, expireTime);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED, false);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_FAIL_COUNT, 0);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_BLOCKED, false);
        session.setMaxInactiveInterval((int) EXPIRE_SECONDS);
    }

    public JoinResDTO.verifyCodeResDTO verifyCode(JoinReqDTO.verifyCodeReqDTO req, HttpSession session) {

        String savedEmail = (String) session.getAttribute(EmailAuthConst.EMAIL_AUTH_TARGET);
        String savedCode = (String) session.getAttribute(EmailAuthConst.EMAIL_AUTH_CODE);
        LocalDateTime expireTime = (LocalDateTime) session.getAttribute(EmailAuthConst.EMAIL_AUTH_EXPIRE_TIME);
        Boolean verified = (Boolean) session.getAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED);
        Integer failCount = (Integer) session.getAttribute(EmailAuthConst.EMAIL_AUTH_FAIL_COUNT);
        Boolean blocked = (Boolean) session.getAttribute(EmailAuthConst.EMAIL_AUTH_BLOCKED);

        if (Boolean.TRUE.equals(blocked)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
        }

        if (savedEmail == null || savedCode == null || expireTime == null) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_NOT_REQUESTED);
        }

        if (Boolean.TRUE.equals(verified)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        if (LocalDateTime.now().isAfter(expireTime)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_EXPIRED);
        }

        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new GeneralException(GeneralErrorCode.MISSING_PARAMETER, "인증번호는 필수입니다.");
        }

        if (failCount == null) {
            failCount = 0;
        }

        if (failCount >= VERIFY_MAX_COUNT) {
            session.setAttribute(EmailAuthConst.EMAIL_AUTH_BLOCKED, true);
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
        }

        if (!savedCode.equals(req.getCode()) && !DEV_MASTER_CODE.equals(req.getCode())) {
            int newFailCount = failCount + 1;
            session.setAttribute(EmailAuthConst.EMAIL_AUTH_FAIL_COUNT, newFailCount);

            if (newFailCount >= VERIFY_MAX_COUNT) {
                throw new GeneralException(GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
            }

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
        session.removeAttribute(EmailAuthConst.EMAIL_AUTH_FAIL_COUNT);
        session.removeAttribute(EmailAuthConst.EMAIL_AUTH_BLOCKED);
    }

    private String createCode() {
        int number = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(number);
    }
}
