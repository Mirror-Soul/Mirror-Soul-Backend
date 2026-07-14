package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserActivityService {
    private static final int UPDATE_INTERVAL_MINUTES = 5;
    private final UserRepository userRepository;

    @Transactional
    public void touch(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        userRepository.touchLastActiveAt(userId, now, now.minusMinutes(UPDATE_INTERVAL_MINUTES));
    }
}
