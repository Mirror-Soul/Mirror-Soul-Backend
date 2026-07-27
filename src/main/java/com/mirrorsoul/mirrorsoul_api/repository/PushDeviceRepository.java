package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.PushDevice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByInstallationId(UUID installationId);

    Optional<PushDevice> findByPushToken(String pushToken);

    Optional<PushDevice> findByInstallationIdAndUserUuid(UUID installationId, UUID userUuid);
}
