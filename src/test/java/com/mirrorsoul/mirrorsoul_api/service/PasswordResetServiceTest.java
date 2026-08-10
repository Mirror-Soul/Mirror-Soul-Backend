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
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.auth.PasswordResetReqDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {

    private UserRepository userRepository;
    private MailService mailService;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mailService = mock(MailService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new PasswordResetService(userRepository, mailService, passwordEncoder);
    }

    @Test
    void sendCodeSendsMailAndCreatesResetSessionForExistingUser() {
        String email = "user@example.com";
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        MockHttpSession session = new MockHttpSession();

        service.sendCode(new PasswordResetReqDTO.SendCodeDTO(email), session);

        verify(mailService).sendPasswordResetCode(
                org.mockito.ArgumentMatchers.eq(email),
                org.mockito.ArgumentMatchers.matches("\\d{6}")
        );
        assertThat(session.getAttribute(PasswordResetConst.TARGET)).isEqualTo(email);
        assertThat(session.getAttribute(PasswordResetConst.CODE).toString()).matches("\\d{6}");
        assertThat(session.getAttribute(PasswordResetConst.VERIFIED)).isEqualTo(false);
    }

    @Test
    void sendCodeDoesNotRevealMissingAccountByThrowingOrSendingMail() {
        String email = "missing@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        MockHttpSession session = new MockHttpSession();

        service.sendCode(new PasswordResetReqDTO.SendCodeDTO(email), session);

        verify(mailService, never()).sendPasswordResetCode(anyString(), anyString());
        assertThat(session.getAttribute(PasswordResetConst.TARGET)).isEqualTo(email);
    }

    @Test
    void verifyCodeMarksSessionAsVerifiedAndMakesCodeSingleUse() {
        MockHttpSession session = resetSession("user@example.com", "654321");

        service.verifyCode(new PasswordResetReqDTO.VerifyCodeDTO("654321"), session);

        assertThat(session.getAttribute(PasswordResetConst.VERIFIED)).isEqualTo(true);
        assertThat(session.getAttribute(PasswordResetConst.CODE)).isNull();
    }

    @Test
    void verifyCodeRejectsExpiredCode() {
        MockHttpSession session = resetSession("user@example.com", "654321");
        session.setAttribute(
                PasswordResetConst.EXPIRE_TIME,
                LocalDateTime.now().minusSeconds(1)
        );

        assertError(
                () -> service.verifyCode(
                        new PasswordResetReqDTO.VerifyCodeDTO("654321"),
                        session
                ),
                GeneralErrorCode.EMAIL_CODE_EXPIRED
        );
    }

    @Test
    void resetPasswordEncodesPasswordAndClearsResetSession() {
        String email = "user@example.com";
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1")).thenReturn("encoded-password");
        MockHttpSession session = resetSession(email, null);
        session.setAttribute(PasswordResetConst.VERIFIED, true);

        service.resetPassword(
                new PasswordResetReqDTO.ResetPasswordDTO(
                        "newPassword1",
                        "newPassword1"
                ),
                session
        );

        verify(user).updatePassword("encoded-password");
        assertThat(session.getAttribute(PasswordResetConst.TARGET)).isNull();
        assertThat(session.getAttribute(PasswordResetConst.VERIFIED)).isNull();
        assertThat(session.getAttribute(PasswordResetConst.EXPIRE_TIME)).isNull();
    }

    @Test
    void resetPasswordRejectsMismatchedConfirmation() {
        MockHttpSession session = resetSession("user@example.com", null);
        session.setAttribute(PasswordResetConst.VERIFIED, true);

        assertError(
                () -> service.resetPassword(
                        new PasswordResetReqDTO.ResetPasswordDTO(
                                "newPassword1",
                                "differentPassword1"
                        ),
                        session
                ),
                GeneralErrorCode.PASSWORD_CONFIRM_MISMATCH
        );

        verify(passwordEncoder, never()).encode(anyString());
    }

    private MockHttpSession resetSession(String email, String code) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PasswordResetConst.TARGET, email);
        if (code != null) {
            session.setAttribute(PasswordResetConst.CODE, code);
        }
        session.setAttribute(
                PasswordResetConst.EXPIRE_TIME,
                LocalDateTime.now().plusMinutes(3)
        );
        session.setAttribute(PasswordResetConst.VERIFIED, false);
        session.setAttribute(PasswordResetConst.FAIL_COUNT, 0);
        session.setAttribute(PasswordResetConst.BLOCKED, false);
        return session;
    }

    private void assertError(Runnable invocation, GeneralErrorCode expectedCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode)
                );
    }
}
