package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.jwt.TokenProvider;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.login.LoginReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.login.LoginResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.PushDeviceRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountWithdrawalLifecycleTest {

    @Test
    void loginWithinThirtyDaysReactivatesWithdrawnAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenProvider tokenProvider = mock(TokenProvider.class);
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenProvider);
        User user = activeUser();
        user.deactivate(LocalDateTime.now().minusDays(29));

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(tokenProvider.createAccessToken(user)).thenReturn("access-token");
        when(tokenProvider.createRefreshToken(user)).thenReturn("refresh-token");

        LoginResDTO response = service.login(new LoginReqDTO("user@example.com", "password"));

        assertThat(response.userStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getWithdrawnAt()).isNull();
        assertThat(user.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginAfterThirtyDaysDoesNotReactivateAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenProvider tokenProvider = mock(TokenProvider.class);
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenProvider);
        User user = activeUser();
        user.deactivate(LocalDateTime.now().minusDays(30).minusMinutes(1));

        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginReqDTO("user@example.com", "password")))
                .isInstanceOf(GeneralException.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void cleanupAnonymizesExpiredAccountAndDeletesPushDevices() {
        UserRepository userRepository = mock(UserRepository.class);
        PushDeviceRepository pushDeviceRepository = mock(PushDeviceRepository.class);
        WithdrawnAccountCleanupService service =
                new WithdrawnAccountCleanupService(userRepository, pushDeviceRepository);
        User user = activeUser();
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 3, 0);
        user.deactivate(now.minusDays(30));

        when(userRepository.findAllByStatusAndWithdrawnAtLessThanEqual(
                UserStatus.INACTIVE, now.minusDays(30))).thenReturn(List.of(user));

        assertThat(service.anonymizeExpiredAccounts(now)).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).startsWith("deleted-").endsWith("@deleted.invalid");
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getDeletedAt()).isEqualTo(now);
        assertThat(user.getMatchingEnabled()).isFalse();
        verify(pushDeviceRepository).deleteAllByUserUuidIn(List.of(user.getUuid()));
    }

    private User activeUser() {
        return User.builder()
                .uuid(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("encoded-password")
                .name("사용자")
                .status(UserStatus.ACTIVE)
                .build();
    }
}
