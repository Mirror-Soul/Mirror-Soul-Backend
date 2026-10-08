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
import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.dto.join.JoinReqDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkTimeTransactionRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class JoinServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private CloneRepository cloneRepository;
    private TalkTimeTransactionRepository talkTimeTransactionRepository;
    private JoinService joinService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cloneRepository = mock(CloneRepository.class);
        talkTimeTransactionRepository = mock(TalkTimeTransactionRepository.class);
        joinService = new JoinService(
                userRepository,
                passwordEncoder,
                cloneRepository,
                mock(TokenProvider.class),
                talkTimeTransactionRepository
        );
    }

    @Test
    void basicProfileRecordsInitialTalkTimeGrant() {
        JoinReqDTO.basicProfileReqDTO request = request("new@example.com");
        HttpSession session = verifiedSession(request.getEmail());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        joinService.basicProfile(request, session);

        ArgumentCaptor<TalkTimeTransaction> transactionCaptor =
                ArgumentCaptor.forClass(TalkTimeTransaction.class);
        verify(talkTimeTransactionRepository).save(transactionCaptor.capture());
        TalkTimeTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getReason()).isEqualTo(TalkTimeTransactionReason.SIGNUP_GRANT);
        assertThat(transaction.getDeltaSeconds()).isEqualTo(1800);
        assertThat(transaction.getBalanceAfterSeconds()).isEqualTo(1800);
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
