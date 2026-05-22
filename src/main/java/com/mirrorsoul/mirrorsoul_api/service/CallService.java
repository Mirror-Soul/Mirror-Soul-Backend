package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.CallResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CallService {

    private static final String SIGNALING_URL = "/ws/signaling";

    private final VideoCallRepository videoCallRepository;
    private final UserRepository userRepository;
    private final CloneRepository cloneRepository;

    @Transactional
    public CallResDTO.StartCallDTO startCloneCall(UUID cloneUserUuid, CallReqDTO.StartCallDTO request) {
        User caller = userRepository.findByUuid(request.callerUserUuid())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        Clone clone = cloneRepository.findByUserUuid(cloneUserUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));

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

        UUID callerUserUuid = caller.getUuid();
        UUID targetCloneUserUuid = clone.getUser().getUuid();

        return CallResDTO.StartCallDTO.builder()
                .callId(savedCall.getId())
                .roomId(savedCall.getRoomId())
                .mediaType(savedCall.getMediaType())
                .status(savedCall.getStatus())
                .callerUserUuid(callerUserUuid)
                .cloneUserUuid(targetCloneUserUuid)
                .userSignalId(userSignalId(callerUserUuid))
                .cloneSignalId(cloneSignalId(targetCloneUserUuid))
                .signalingUrl(SIGNALING_URL)
                .build();
    }

    @Transactional
    public void markInProgress(Long callId) {
        VideoCall call = getCall(callId);

        if (call.getStatus() == VideoCallStatus.COMPLETED ||
                call.getStatus() == VideoCallStatus.CANCELLED ||
                call.getStatus() == VideoCallStatus.FAILED) {
            throw new GeneralException(GeneralErrorCode.CALL_ALREADY_ENDED);
        }

        call.start();
    }

    @Transactional
    public CallResDTO.EndCallDTO endCall(Long callId, CallReqDTO.EndCallDTO request) {
        VideoCall call = getCall(callId);

        if (call.getStatus() == VideoCallStatus.COMPLETED ||
                call.getStatus() == VideoCallStatus.CANCELLED ||
                call.getStatus() == VideoCallStatus.FAILED) {
            throw new GeneralException(GeneralErrorCode.CALL_ALREADY_ENDED);
        }

        if (request != null && request.recordingUrl() != null && !request.recordingUrl().isBlank()) {
            call.updateRecordingUrl(request.recordingUrl());
        }

        call.complete();

        return CallResDTO.EndCallDTO.builder()
                .callId(call.getId())
                .status(call.getStatus())
                .durationSec(call.getDurationSec())
                .build();
    }

    private VideoCall getCall(Long callId) {
        return videoCallRepository.findById(callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));
    }

    private String userSignalId(UUID userUuid) {
        return "user:" + userUuid;
    }

    private String cloneSignalId(UUID cloneUserUuid) {
        return "clone:" + cloneUserUuid;
    }
}