package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.PushDevice;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.push.PushDeviceReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.push.PushDeviceResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.PushDeviceRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final UserRepository userRepository;

    @Transactional
    public PushDeviceResDTO.DeviceDTO register(
            UUID userUuid, PushDeviceReqDTO.RegisterDTO request) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        Optional<PushDevice> byInstallation =
                pushDeviceRepository.findByInstallationId(request.installationId());
        Optional<PushDevice> byToken =
                pushDeviceRepository.findByPushToken(request.pushToken().trim());

        PushDevice device;
        if (byInstallation.isPresent()) {
            device = byInstallation.get();
            if (byToken.isPresent() && !byToken.get().getId().equals(device.getId())) {
                pushDeviceRepository.delete(byToken.get());
                pushDeviceRepository.flush();
            }
        } else if (byToken.isPresent()) {
            device = byToken.get();
        } else {
            device = PushDevice.builder().build();
        }

        device.register(
                user,
                request.installationId(),
                request.pushToken().trim(),
                request.platform(),
                LocalDateTime.now()
        );
        return toDTO(pushDeviceRepository.save(device));
    }

    @Transactional
    public void unregister(UUID userUuid, UUID installationId) {
        pushDeviceRepository.findByInstallationIdAndUserUuid(installationId, userUuid)
                .ifPresent(pushDeviceRepository::delete);
    }

    private PushDeviceResDTO.DeviceDTO toDTO(PushDevice device) {
        return PushDeviceResDTO.DeviceDTO.builder()
                .installationId(device.getInstallationId())
                .platform(device.getPlatform())
                .enabled(device.isEnabled())
                .lastSeenAt(device.getLastSeenAt())
                .build();
    }
}
