package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
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
import com.mirrorsoul.mirrorsoul_api.repository.UserBlockRepository;
import com.mirrorsoul.mirrorsoul_api.billing.*;
import java.time.*;
import java.util.List;
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
    private final TalkTimeLedger ledger;
    private final CallBillingEventRepository billingEvents;

    @Transactional
    public CallResDTO.StartCallDTO startCloneCall(UUID cloneUserUuid, CallReqDTO.StartCallDTO request, UUID userUUID) {
        User caller = userRepository.findByUuidForUpdate(userUUID)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        if (!caller.isAiWorkAllowed(java.time.LocalDateTime.now())) {
            throw new GeneralException(GeneralErrorCode.SAFETY_RESTRICTED);
        }
        if (ledger.balance(caller.getId()) <= 0) {
            throw new GeneralException(GeneralErrorCode.INSUFFICIENT_TALK_TIME);
        }
        if (videoCallRepository.existsByUserIdAndStatusIn(caller.getId(),
                List.of(VideoCallStatus.READY, VideoCallStatus.IN_PROGRESS))) {
            throw new GeneralException(GeneralErrorCode.PAYMENT_CONFLICT, "이미 진행 중인 통화가 있습니다.");
        }

        Clone clone = cloneRepository.findByUserUuid(cloneUserUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));
        User cloneOwner = clone.getUser();
        if (!cloneOwner.isAiWorkAllowed(java.time.LocalDateTime.now())) {
            throw new GeneralException(GeneralErrorCode.SAFETY_RESTRICTED);
        }
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
                .correlationId(UUID.fromString(savedCall.diagnosticCorrelationId()))
                .cloneVersion(savedCall.getCloneVersion())
                .trainingJobIds(com.mirrorsoul.mirrorsoul_api.quality.CallDiagnosticsReadService.trainingIds(savedCall))
                .roomId(savedRoomId)
                .mediaType(savedCall.getMediaType())
                .status(savedCall.getStatus())
                .callerSignalId(callerSignalId(savedRoomId))
                .aiSignalId(aiSignalId(savedRoomId))
                .signalingUrl(SIGNALING_URL)
                .build();
    }

    @Transactional
    public void markInProgress(Long callId, UUID callerUuid) {
        VideoCall call = getCall(callId);
        User caller = userRepository.findByIdForUpdate(call.getUser().getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if (!caller.getUuid().equals(callerUuid)) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        markInProgress(callId);
    }

    @Transactional
    public void markInProgress(Long callId) {
        connect(getCall(callId), LocalDateTime.now());
    }

    private void connect(VideoCall call, LocalDateTime at) {
        User caller = userRepository.findByIdForUpdate(call.getUser().getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if (!caller.isAiWorkAllowed(java.time.LocalDateTime.now())
                || !call.getClone().getUser().isAiWorkAllowed(java.time.LocalDateTime.now())) {
            throw new GeneralException(GeneralErrorCode.SAFETY_RESTRICTED);
        }
        if (userBlockRepository.existsBetween(
                call.getUser().getId(), call.getClone().getUser().getId())) {
            throw new GeneralException(GeneralErrorCode.CALL_NOT_FOUND);
        }

        if (call.getStatus() == VideoCallStatus.COMPLETED ||
                call.getStatus() == VideoCallStatus.CANCELLED ||
                call.getStatus() == VideoCallStatus.FAILED) {
            throw new GeneralException(GeneralErrorCode.CALL_ALREADY_ENDED);
        }

        if (call.getStatus() == VideoCallStatus.IN_PROGRESS) return;
        int available = Math.max(0, ledger.balance(caller.getId()));
        if (available == 0) throw new GeneralException(GeneralErrorCode.INSUFFICIENT_TALK_TIME);
        call.start(at, available);
    }

    @Transactional
    public CallResDTO.EndCallDTO endCall(Long callId, CallReqDTO.EndCallDTO request, UUID userUuid) {
        VideoCall call = videoCallRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));

        User caller = userRepository.findByIdForUpdate(call.getUser().getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if (!caller.getUuid().equals(userUuid)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

        if (terminal(call)) return endResult(call);

        if (request != null && request.recordingUrl() != null && !request.recordingUrl().isBlank()) {
            call.updateRecordingUrl(request.recordingUrl());
        }

        settle(call, LocalDateTime.now(), "USER_HANGUP", "MEMBER");
        return endResult(call);
    }

    private boolean terminal(VideoCall call) {
        return call.getStatus() == VideoCallStatus.COMPLETED || call.getStatus() == VideoCallStatus.CANCELLED
                || call.getStatus() == VideoCallStatus.FAILED;
    }
    private void settle(VideoCall call, LocalDateTime at, String reason, String source) {
        User caller = userRepository.findByIdForUpdate(call.getUser().getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        call.finish(at, reason, source);
        // Calls admitted by this billing contract have a fixed deadline. Charge their
        // measured usage even if a concurrent refund has already removed the funds.
        int debit = call.getBillingLimitSeconds() == null
                ? -Math.min(Math.max(0, ledger.balance(caller.getId())), call.getDurationSec())
                : -call.getDurationSec();
        ledger.append(caller, call, TalkTimeTransactionReason.CALL_USAGE, debit, null,
                "call:" + call.getId(), null, reason, null);
    }
    private CallResDTO.EndCallDTO endResult(VideoCall call) {
        return CallResDTO.EndCallDTO.builder()
                .callId(call.getId())
                .status(call.getStatus())
                .durationSec(call.getDurationSec())
                .remainingTalkTime(Math.max(0, ledger.balance(call.getUser().getId())))
                .build();
    }

    @Transactional
    public CallBillingDtos.Result billingEvent(Long callId, CallBillingDtos.Event event) {
        VideoCall call = getCall(callId);
        if (event.kind() == null || event.occurredAt() == null || event.eventId() == null || event.eventId().isBlank()
                || event.kind() == CallBillingDtos.Kind.ENDED && event.endReason() == null
                || event.kind() == CallBillingDtos.Kind.STARTED && event.endReason() != null) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
        String key = BillingSupport.hash(callId + ":" + event.eventId());
        String hash = BillingSupport.hash(event.kind() + ":" + event.occurredAt().toInstant() + ":" + event.endReason());
        var previous = billingEvents.findByEventKey(key);
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(hash)) throw new GeneralException(GeneralErrorCode.PAYMENT_CONFLICT);
            return billingResult(call);
        }
        LocalDateTime at = LocalDateTime.ofInstant(event.occurredAt().toInstant(), ZoneId.systemDefault());
        if (at.isAfter(LocalDateTime.now().plusSeconds(5)) || at.isBefore(call.getStartedAt().minusSeconds(5))) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
        if (at.isBefore(call.getStartedAt())) at = call.getStartedAt();
        if (!terminal(call)) {
            if (event.kind() == CallBillingDtos.Kind.STARTED) connect(call, at);
            else {
                if (call.getConnectedAt() == null && event.endReason() != CallBillingDtos.EndReason.CANCELLED)
                    throw new GeneralException(GeneralErrorCode.PAYMENT_CONFLICT, "통화 시작 이벤트를 먼저 처리해 주세요.");
                if (call.getConnectedAt() != null && at.isBefore(call.getConnectedAt()))
                    throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
                settle(call, at, event.endReason().name(), "AI");
            }
        }
        billingEvents.saveAndFlush(new CallBillingEvent(key, hash, callId, event.kind().name(), at));
        return billingResult(call);
    }
    private CallBillingDtos.Result billingResult(VideoCall call) {
        return new CallBillingDtos.Result(call.getId(), call.getStatus(), Math.max(0, ledger.balance(call.getUser().getId())),
                call.getBillingLimitSeconds(), call.getBillingDeadlineAt());
    }
    @Transactional
    public void endExpired(Long callId) {
        VideoCall call = getCall(callId);
        if (!terminal(call) && call.getBillingDeadlineAt() != null
                && !call.getBillingDeadlineAt().isAfter(LocalDateTime.now()))
            settle(call, call.getBillingDeadlineAt(), "TIME_EXHAUSTED", "SERVER");
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
