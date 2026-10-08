package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkTimeTransactionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserBlockRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CallService {

    private static final String SIGNALING_URL = "/ws/signaling";
    private static final String READY_CLONE_STATUS = "READY";

    private final VideoCallRepository videoCallRepository;
    private final UserRepository userRepository;
    private final CloneRepository cloneRepository;
    private final UserBlockRepository userBlockRepository;
    private final TalkTimeTransactionRepository talkTimeTransactionRepository;

    @Transactional
    public CallResDTO.StartCallDTO startCloneCall(UUID cloneUserUuid, CallReqDTO.StartCallDTO request, UUID userUUID) {
        User caller = userRepository.findByUuid(userUUID)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        if (!caller.hasTalkTime()) {
            throw new GeneralException(GeneralErrorCode.INSUFFICIENT_TALK_TIME);
        }

        Clone clone = cloneRepository.findByUserUuid(cloneUserUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));
        User cloneOwner = clone.getUser();
        boolean callingOwnClone = caller.getId().equals(cloneOwner.getId());
        if (!callingOwnClone && (
                cloneOwner.getStatus() != com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus.ACTIVE
                        || !Boolean.TRUE.equals(cloneOwner.getMatchingEnabled())
                        || userBlockRepository.existsBetween(caller.getId(), cloneOwner.getId()))) {
            throw new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND);
        }
        if (!callingOwnClone && !READY_CLONE_STATUS.equals(clone.getStatus())) {
            throw new GeneralException(GeneralErrorCode.CLONE_NOT_READY);
        }

        CallMediaType mediaType = request.mediaType() == null
                ? CallMediaType.VOICE
                : request.mediaType();

        String roomId = "call-" + UUID.randomUUID();

        VideoCall call = VideoCall.builder()
                .user(caller)
                .clone(clone)
                .roomId(roomId)
                .mediaType(mediaType)
                .build();

        VideoCall savedCall = videoCallRepository.save(call);
        String savedRoomId = savedCall.getRoomId();

        return CallResDTO.StartCallDTO.builder()
                .callId(savedCall.getId())
                .roomId(savedRoomId)
                .mediaType(savedCall.getMediaType())
                .status(savedCall.getStatus())
                .callerSignalId(callerSignalId(savedRoomId))
                .aiSignalId(aiSignalId(savedRoomId))
                .signalingUrl(SIGNALING_URL)
                .build();
    }

    @Transactional
    public void markInProgress(Long callId) {
        VideoCall call = getCall(callId);

        if (userBlockRepository.existsBetween(
                call.getUser().getId(), call.getClone().getUser().getId())) {
            throw new GeneralException(GeneralErrorCode.CALL_NOT_FOUND);
        }

        if (call.getStatus() == VideoCallStatus.COMPLETED ||
                call.getStatus() == VideoCallStatus.CANCELLED ||
                call.getStatus() == VideoCallStatus.FAILED) {
            throw new GeneralException(GeneralErrorCode.CALL_ALREADY_ENDED);
        }

        call.start();
    }

    @Transactional
    public CallResDTO.EndCallDTO endCall(Long callId, CallReqDTO.EndCallDTO request, UUID userUuid) {
        VideoCall call = videoCallRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));

        if (!call.getUser().getUuid().equals(userUuid)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

        if (call.getStatus() == VideoCallStatus.COMPLETED ||
                call.getStatus() == VideoCallStatus.CANCELLED ||
                call.getStatus() == VideoCallStatus.FAILED) {
            throw new GeneralException(GeneralErrorCode.CALL_ALREADY_ENDED);
        }

        if (request != null && request.recordingUrl() != null && !request.recordingUrl().isBlank()) {
            call.updateRecordingUrl(request.recordingUrl());
        }

        User caller = userRepository.findByIdForUpdate(call.getUser().getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        call.complete();
        int balanceBefore = caller.getRemainingTalkTime();
        caller.useTalkTime(call.getDurationSec());
        talkTimeTransactionRepository.save(TalkTimeTransaction.record(
                caller, call, TalkTimeTransactionReason.CALL_USAGE,
                caller.getRemainingTalkTime() - balanceBefore));

        return CallResDTO.EndCallDTO.builder()
                .callId(call.getId())
                .status(call.getStatus())
                .durationSec(call.getDurationSec())
                .remainingTalkTime(caller.getRemainingTalkTime())
                .build();
    }

    private VideoCall getCall(Long callId) {
        return videoCallRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));
    }

    private String callerSignalId(String roomId) {
        return "signal:" + roomId + ":caller";
    }

    private String aiSignalId(String roomId) {
        return "signal:" + roomId + ":ai";
    }

}
