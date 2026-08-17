package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserBlock;
import com.mirrorsoul.mirrorsoul_api.repository.MeetingRequestRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserBlockRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBlockService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final MeetingRequestRepository meetingRequestRepository;

    @Transactional
    public void block(UUID blockerUuid, UUID blockedUuid) {
        if (blockerUuid.equals(blockedUuid)) {
            throw new GeneralException(GeneralErrorCode.BLOCK_SELF_NOT_ALLOWED);
        }

        User blocker = requireUser(blockerUuid, GeneralErrorCode.USER_NOT_FOUND);
        User blocked = requireUser(blockedUuid, GeneralErrorCode.BLOCK_TARGET_NOT_FOUND);
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blocked.getId())) {
            return;
        }

        userBlockRepository.saveAndFlush(UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build());

        meetingRequestRepository.findPendingBetween(blocker.getId(), blocked.getId())
                .forEach(request -> request.reject());
    }

    @Transactional
    public void unblock(UUID blockerUuid, UUID blockedUuid) {
        User blocker = requireUser(blockerUuid, GeneralErrorCode.USER_NOT_FOUND);
        User blocked = requireUser(blockedUuid, GeneralErrorCode.BLOCK_TARGET_NOT_FOUND);
        userBlockRepository.deleteByBlockerIdAndBlockedId(blocker.getId(), blocked.getId());
    }

    private User requireUser(UUID uuid, GeneralErrorCode errorCode) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new GeneralException(errorCode));
    }
}
