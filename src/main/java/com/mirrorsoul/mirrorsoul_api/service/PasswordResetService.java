package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.mail.PasswordResetConst;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.login.PasswordResetReqDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final int VERIFY_MAX_COUNT = 5;
    private static final int EXPIRE_SECONDS = 180;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MailService mailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void sendCode(PasswordResetReqDTO.SendCode request, HttpSession session) {
        String email = request.email();
        if (!userRepository.existsByEmail(email)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_NOT_FOUND);
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        mailService.sendVerificationCode(email, code);

        clearSession(session);
        session.setAttribute(PasswordResetConst.TARGET_EMAIL, email);
        session.setAttribute(PasswordResetConst.CODE, code);
        session.setAttribute(PasswordResetConst.EXPIRE_TIME, LocalDateTime.now().plusSeconds(EXPIRE_SECONDS));
        session.setAttribute(PasswordResetConst.VERIFIED, false);
        session.setAttribute(PasswordResetConst.FAIL_COUNT, 0);
        session.setAttribute(PasswordResetConst.BLOCKED, false);
        session.setMaxInactiveInterval(EXPIRE_SECONDS);
    }

    public void verifyCode(PasswordResetReqDTO.VerifyCode request, HttpSession session) {
        String savedEmail = (String) session.getAttribute(PasswordResetConst.TARGET_EMAIL);
        String savedCode = (String) session.getAttribute(PasswordResetConst.CODE);
        LocalDateTime expireTime = (LocalDateTime) session.getAttribute(PasswordResetConst.EXPIRE_TIME);
        Integer failCount = (Integer) session.getAttribute(PasswordResetConst.FAIL_COUNT);

        if (savedEmail == null || savedCode == null || expireTime == null) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_NOT_REQUESTED);
        }
        if (!savedEmail.equals(request.email())) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO);
        }
        if (Boolean.TRUE.equals(session.getAttribute(PasswordResetConst.BLOCKED))) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
        }
        if (Boolean.TRUE.equals(session.getAttribute(PasswordResetConst.VERIFIED))) {
            throw new GeneralException(GeneralErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        if (LocalDateTime.now().isAfter(expireTime)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_EXPIRED);
        }

        int attempts = failCount == null ? 0 : failCount;
        if (!savedCode.equals(request.code())) {
            int newFailCount = attempts + 1;
            session.setAttribute(PasswordResetConst.FAIL_COUNT, newFailCount);
            if (newFailCount >= VERIFY_MAX_COUNT) {
                session.setAttribute(PasswordResetConst.BLOCKED, true);
                throw new GeneralException(GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
            }
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_MISMATCH);
        }
        session.setAttribute(PasswordResetConst.VERIFIED, true);
        session.removeAttribute(PasswordResetConst.CODE);
    }

    @Transactional
    public void resetPassword(PasswordResetReqDTO.ResetPassword request, HttpSession session) {
        String savedEmail = (String) session.getAttribute(PasswordResetConst.TARGET_EMAIL);
        Boolean verified = (Boolean) session.getAttribute(PasswordResetConst.VERIFIED);
        if (savedEmail == null || !savedEmail.equals(request.email()) || !Boolean.TRUE.equals(verified)) {
            throw new GeneralException(GeneralErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new GeneralException(GeneralErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        User user = userRepository.findByEmail(savedEmail)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        user.clearRefreshToken();
        clearSession(session);
    }

    private void clearSession(HttpSession session) {
        session.removeAttribute(PasswordResetConst.TARGET_EMAIL);
        session.removeAttribute(PasswordResetConst.CODE);
        session.removeAttribute(PasswordResetConst.EXPIRE_TIME);
        session.removeAttribute(PasswordResetConst.VERIFIED);
        session.removeAttribute(PasswordResetConst.FAIL_COUNT);
        session.removeAttribute(PasswordResetConst.BLOCKED);
    }
}
