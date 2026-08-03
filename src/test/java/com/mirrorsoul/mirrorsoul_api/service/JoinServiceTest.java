package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.jwt.TokenProvider;
import com.mirrorsoul.mirrorsoul_api.common.mail.EmailAuthConst;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinReqDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class JoinServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private CloneRepository cloneRepository;
    private JoinService joinService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cloneRepository = mock(CloneRepository.class);
        joinService = new JoinService(
                userRepository,
                passwordEncoder,
                cloneRepository,
                mock(TokenProvider.class)
        );
    }

    @Test
    void basicProfileRejectsEmailAlreadyInUse() {
        JoinReqDTO.basicProfileReqDTO request = request("duplicate@example.com");
        HttpSession session = verifiedSession(request.getEmail());
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertDuplicateEmail(() -> joinService.basicProfile(request, session));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void basicProfileTranslatesConcurrentUniqueConstraintViolation() {
        JoinReqDTO.basicProfileReqDTO request = request("race@example.com");
        HttpSession session = verifiedSession(request.getEmail());
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertDuplicateEmail(() -> joinService.basicProfile(request, session));

        verify(cloneRepository, never()).save(any());
    }

    private void assertDuplicateEmail(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.DUPLICATE_EMAIL));
    }

    private JoinReqDTO.basicProfileReqDTO request(String email) {
        JoinReqDTO.basicProfileReqDTO request = new JoinReqDTO.basicProfileReqDTO();
        request.setEmail(email);
        request.setPassword("password1");
        request.setTermsAgreed(true);
        return request;
    }

    private HttpSession verifiedSession(String email) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(EmailAuthConst.EMAIL_AUTH_TARGET)).thenReturn(email);
        when(session.getAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED)).thenReturn(true);
        return session;
    }
}
