package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.PushDevice;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.PushDevicePlatform;
import com.mirrorsoul.mirrorsoul_api.dto.push.PushDeviceReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.push.PushDeviceResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.PushDeviceRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PushDeviceServiceTest {

    private PushDeviceRepository pushDeviceRepository;
    private UserRepository userRepository;
    private PushDeviceService pushDeviceService;

    @BeforeEach
    void setUp() {
        pushDeviceRepository = mock(PushDeviceRepository.class);
        userRepository = mock(UserRepository.class);
        pushDeviceService = new PushDeviceService(pushDeviceRepository, userRepository);
    }

    @Test
    void registerCreatesNewIosDevice() {
        UUID userUuid = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        User user = user(1L, userUuid);
        PushDeviceReqDTO.RegisterDTO request = new PushDeviceReqDTO.RegisterDTO(
                installationId, "ios-token", PushDevicePlatform.IOS);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(pushDeviceRepository.findByInstallationId(installationId)).thenReturn(Optional.empty());
        when(pushDeviceRepository.findByPushToken("ios-token")).thenReturn(Optional.empty());
        when(pushDeviceRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PushDeviceResDTO.DeviceDTO result = pushDeviceService.register(userUuid, request);

        assertThat(result.installationId()).isEqualTo(installationId);
        assertThat(result.platform()).isEqualTo(PushDevicePlatform.IOS);
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void registerUpdatesTokenAndPlatformForExistingInstallation() {
        UUID userUuid = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        User user = user(1L, userUuid);
        PushDevice existing = device(
                10L, user, installationId, "old-token", PushDevicePlatform.IOS);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(pushDeviceRepository.findByInstallationId(installationId))
                .thenReturn(Optional.of(existing));
        when(pushDeviceRepository.findByPushToken("new-token")).thenReturn(Optional.empty());
        when(pushDeviceRepository.save(existing)).thenReturn(existing);

        pushDeviceService.register(userUuid, new PushDeviceReqDTO.RegisterDTO(
                installationId, "new-token", PushDevicePlatform.ANDROID));

        assertThat(existing.getPushToken()).isEqualTo("new-token");
        assertThat(existing.getPlatform()).isEqualTo(PushDevicePlatform.ANDROID);
        assertThat(existing.getUser()).isEqualTo(user);
    }

    @Test
    void unregisterDeletesOnlyCurrentUsersInstallation() {
        UUID userUuid = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        PushDevice device = device(
                10L, user(1L, userUuid), installationId, "token", PushDevicePlatform.IOS);
        when(pushDeviceRepository.findByInstallationIdAndUserUuid(installationId, userUuid))
                .thenReturn(Optional.of(device));

        pushDeviceService.unregister(userUuid, installationId);

        verify(pushDeviceRepository).delete(device);
    }

    @Test
    void unregisterUnknownInstallationIsIdempotent() {
        UUID userUuid = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        when(pushDeviceRepository.findByInstallationIdAndUserUuid(installationId, userUuid))
                .thenReturn(Optional.empty());

        pushDeviceService.unregister(userUuid, installationId);

        verify(pushDeviceRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private User user(Long id, UUID uuid) {
        return User.builder()
                .id(id)
                .uuid(uuid)
                .email(uuid + "@example.com")
                .passwordHash("password")
                .build();
    }

    private PushDevice device(
            Long id,
            User user,
            UUID installationId,
            String token,
            PushDevicePlatform platform) {
        return PushDevice.builder()
                .id(id)
                .user(user)
                .installationId(installationId)
                .pushToken(token)
                .platform(platform)
                .enabled(true)
                .lastSeenAt(LocalDateTime.now())
                .build();
    }
}
