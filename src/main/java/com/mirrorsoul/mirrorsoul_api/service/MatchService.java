package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.match.MatchResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {

    private final VideoCallRepository videoCallRepository;

    public MatchResDTO.TwinListDTO getTwins(UUID userUuid) {
        List<VideoCall> completedCalls = videoCallRepository.findAllByUserUuidAndStatusOrderByLatest(
                userUuid,
                VideoCallStatus.COMPLETED
        );

        Map<UUID, TwinCallHistory> historiesByCloneUser = new LinkedHashMap<>();
        for (VideoCall call : completedCalls) {
            UUID cloneUserUuid = call.getClone().getUser().getUuid();
            historiesByCloneUser.compute(
                    cloneUserUuid,
                    (uuid, history) -> history == null
                            ? new TwinCallHistory(call)
                            : history.addCall()
            );
        }

        List<MatchResDTO.TwinDTO> twins = historiesByCloneUser.values().stream()
                .map(this::toTwinDTO)
                .toList();

        return MatchResDTO.TwinListDTO.builder()
                .totalCount(twins.size())
                .twins(twins)
                .build();
    }

    private MatchResDTO.TwinDTO toTwinDTO(TwinCallHistory history) {
        VideoCall latestCall = history.latestCall();
        Clone twin = latestCall.getClone();
        User twinOwner = twin.getUser();

        return MatchResDTO.TwinDTO.builder()
                .cloneUserUuid(twinOwner.getUuid())
                .name(twinOwner.getName())
                .age(calculateAge(twinOwner.getBirthDate()))
                .profileImageUrl(twinOwner.getProfileImageUrl())
                .twinAvatarImageUrl(twin.getAvatarImageUrl())
                .twinSyncRate(twin.getSyncRate())
                .twinSummary(twin.getSummary())
                .latestCallId(latestCall.getId())
                .latestCallMediaType(latestCall.getMediaType())
                .lastCalledAt(latestCall.getEndedAt())
                .totalCallCount(history.totalCallCount())
                .build();
    }

    private Integer calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private record TwinCallHistory(VideoCall latestCall, int totalCallCount) {

        private TwinCallHistory(VideoCall latestCall) {
            this(latestCall, 1);
        }

        private TwinCallHistory addCall() {
            return new TwinCallHistory(latestCall, totalCallCount + 1);
        }
    }
}
