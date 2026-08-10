package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.mail.PasswordResetConst;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.login.PasswordResetReqDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {
    private MailService mailService;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        mailService = mock(MailService.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new PasswordResetService(mailService, userRepository, passwordEncoder);
    }

    @Test
    void sendCodeRejectsUnregisteredEmail() {
        var request = new PasswordResetReqDTO.SendCode("missing@example.com");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);

        assertError(() -> service.sendCode(request, new MockHttpSession()), GeneralErrorCode.EMAIL_NOT_FOUND);
        verify(mailService, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void verifyCodeMarksSessionAsVerified() {
        MockHttpSession session = resetSession("user@example.com", "654321");

        service.verifyCode(new PasswordResetReqDTO.VerifyCode("user@example.com", "654321"), session);

        assertThat(session.getAttribute(PasswordResetConst.VERIFIED)).isEqualTo(true);
        assertThat(session.getAttribute(PasswordResetConst.CODE)).isNull();
    }

    @Test
    void verifyCodeBlocksAfterFiveFailures() {
        MockHttpSession session = resetSession("user@example.com", "654321");
        var wrongRequest = new PasswordResetReqDTO.VerifyCode("user@example.com", "000000");

        for (int count = 0; count < 4; count++) {
            assertError(() -> service.verifyCode(wrongRequest, session), GeneralErrorCode.EMAIL_CODE_MISMATCH);
        }
        assertError(() -> service.verifyCode(wrongRequest, session), GeneralErrorCode.EMAIL_CODE_ATTEMPT_EXCEEDED);
        assertThat(session.getAttribute(PasswordResetConst.BLOCKED)).isEqualTo(true);
    }

    @Test
    void resetPasswordUpdatesHashAndClearsResetSession() {
        String email = "user@example.com";
        MockHttpSession session = resetSession(email, "654321");
        session.setAttribute(PasswordResetConst.VERIFIED, true);
        User user = User.builder().email(email).passwordHash("old-hash").refreshToken("refresh-token").build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

        service.resetPassword(
                new PasswordResetReqDTO.ResetPassword(email, "newPassword1", "newPassword1"),
                session
        );

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getRefreshToken()).isNull();
        assertThat(session.getAttribute(PasswordResetConst.TARGET_EMAIL)).isNull();
    }

    @Test
    void resetPasswordRequiresVerifiedMatchingSession() {
        MockHttpSession session = resetSession("user@example.com", "654321");

        assertError(
                () -> service.resetPassword(
                        new PasswordResetReqDTO.ResetPassword("user@example.com", "newPassword1", "newPassword1"),
                        session
                ),
                GeneralErrorCode.EMAIL_NOT_VERIFIED
        );
        verify(passwordEncoder, never()).encode(anyString());
    }

    private MockHttpSession resetSession(String email, String code) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PasswordResetConst.TARGET_EMAIL, email);
        session.setAttribute(PasswordResetConst.CODE, code);
        session.setAttribute(PasswordResetConst.EXPIRE_TIME, LocalDateTime.now().plusMinutes(3));
        session.setAttribute(PasswordResetConst.VERIFIED, false);
        session.setAttribute(PasswordResetConst.FAIL_COUNT, 0);
        session.setAttribute(PasswordResetConst.BLOCKED, false);
        return session;
    }

    private void assertError(Runnable invocation, GeneralErrorCode expected) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expected));
    }
}
