package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.mail.PasswordResetConst;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.auth.PasswordResetReqDTO;
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
@Transactional(readOnly = true)
public class PasswordResetService {

    private static final int VERIFY_MAX_COUNT = 5;
    private static final int EXPIRE_SECONDS = 180;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;

    public void sendCode(PasswordResetReqDTO.SendCodeDTO request, HttpSession session) {
        String email = request.email().trim();
        String code = createCode();

        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() != UserStatus.INACTIVE)
                .ifPresent(user -> mailService.sendPasswordResetCode(email, code));

        session.setAttribute(PasswordResetConst.TARGET, email);
        session.setAttribute(PasswordResetConst.CODE, code);
        session.setAttribute(
                PasswordResetConst.EXPIRE_TIME,
                LocalDateTime.now().plusSeconds(EXPIRE_SECONDS)
        );
        session.setAttribute(PasswordResetConst.VERIFIED, false);
        session.setAttribute(PasswordResetConst.FAIL_COUNT, 0);
        session.setAttribute(PasswordResetConst.BLOCKED, false);
        session.setMaxInactiveInterval(EXPIRE_SECONDS);
    }

    public void verifyCode(PasswordResetReqDTO.VerifyCodeDTO request, HttpSession session) {
        String savedEmail = attribute(session, PasswordResetConst.TARGET, String.class);
        String savedCode = attribute(session, PasswordResetConst.CODE, String.class);
        LocalDateTime expireTime = attribute(
                session,
                PasswordResetConst.EXPIRE_TIME,
                LocalDateTime.class
        );
        Boolean verified = attribute(session, PasswordResetConst.VERIFIED, Boolean.class);
        Integer failCount = attribute(session, PasswordResetConst.FAIL_COUNT, Integer.class);
        Boolean blocked = attribute(session, PasswordResetConst.BLOCKED, Boolean.class);

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

        int currentFailCount = failCount == null ? 0 : failCount;
        if (currentFailCount >= VERIFY_MAX_COUNT) {
            session.setAttribute(PasswordResetConst.BLOCKED, true);
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
        }
        if (!savedCode.equals(request.code())) {
            int newFailCount = currentFailCount + 1;
            session.setAttribute(PasswordResetConst.FAIL_COUNT, newFailCount);
            if (newFailCount >= VERIFY_MAX_COUNT) {
                session.setAttribute(PasswordResetConst.BLOCKED, true);
                throw new GeneralException(GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
            }
            throw new GeneralException(GeneralErrorCode.EMAIL_CODE_MISMATCH);
        }

        session.setAttribute(PasswordResetConst.VERIFIED, true);
        session.removeAttribute(PasswordResetConst.CODE);
        session.removeAttribute(PasswordResetConst.FAIL_COUNT);
        session.removeAttribute(PasswordResetConst.BLOCKED);
    }

    @Transactional
    public void resetPassword(
            PasswordResetReqDTO.ResetPasswordDTO request,
            HttpSession session
    ) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new GeneralException(GeneralErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        String email = attribute(session, PasswordResetConst.TARGET, String.class);
        Boolean verified = attribute(session, PasswordResetConst.VERIFIED, Boolean.class);
        LocalDateTime expireTime = attribute(
                session,
                PasswordResetConst.EXPIRE_TIME,
                LocalDateTime.class
        );
        if (email == null
                || !Boolean.TRUE.equals(verified)
                || expireTime == null
                || LocalDateTime.now().isAfter(expireTime)) {
            throw new GeneralException(GeneralErrorCode.PASSWORD_RESET_NOT_VERIFIED);
        }

        User user = userRepository.findByEmail(email)
                .filter(candidate -> candidate.getStatus() != UserStatus.INACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.PASSWORD_RESET_NOT_VERIFIED));
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        clearSession(session);
    }

    private void clearSession(HttpSession session) {
        session.removeAttribute(PasswordResetConst.TARGET);
        session.removeAttribute(PasswordResetConst.CODE);
        session.removeAttribute(PasswordResetConst.EXPIRE_TIME);
        session.removeAttribute(PasswordResetConst.VERIFIED);
        session.removeAttribute(PasswordResetConst.FAIL_COUNT);
        session.removeAttribute(PasswordResetConst.BLOCKED);
    }

    private String createCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private <T> T attribute(HttpSession session, String name, Class<T> type) {
        Object value = session.getAttribute(name);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
