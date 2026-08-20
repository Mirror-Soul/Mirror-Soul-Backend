package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.PushDeviceRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawnAccountCleanupService {

    private static final long RECOVERY_PERIOD_DAYS = 30;

    private final UserRepository userRepository;
    private final PushDeviceRepository pushDeviceRepository;

    @Transactional
    public int anonymizeExpiredAccounts(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(RECOVERY_PERIOD_DAYS);
        List<User> expiredUsers = userRepository
                .findAllByStatusAndWithdrawnAtLessThanEqual(UserStatus.INACTIVE, cutoff);

        if (expiredUsers.isEmpty()) {
            return 0;
        }

        List<UUID> userUuids = expiredUsers.stream().map(User::getUuid).toList();
        pushDeviceRepository.deleteAllByUserUuidIn(userUuids);
        expiredUsers.forEach(user -> user.anonymize(now));
        return expiredUsers.size();
    }
}
