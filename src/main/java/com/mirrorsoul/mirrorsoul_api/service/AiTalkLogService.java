package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.TalkLog;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiTalkLogReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiTalkLogResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.TalkLogRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiTalkLogService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final VideoCallRepository videoCallRepository;
    private final TalkLogRepository talkLogRepository;

    @Transactional
    public AiTalkLogResDTO.Saved save(Long callId, AiTalkLogReqDTO.Save request) {
        VideoCall call = videoCallRepository.findByIdForUpdate(callId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));

        String eventId = request.eventId().toString();
        return talkLogRepository.findByVideoCallIdAndEventId(callId, eventId)
                .map(talkLog -> toResponse(talkLog, true))
                .orElseGet(() -> saveNew(call, eventId, request));
    }

    private AiTalkLogResDTO.Saved saveNew(
            VideoCall call,
            String eventId,
            AiTalkLogReqDTO.Save request
    ) {
        validateCallStatus(call.getStatus());
        validateTimeRange(request.startedAt(), request.endedAt());

        TalkLog talkLog = TalkLog.builder()
                .videoCall(call)
                .eventId(eventId)
                .speaker(request.speaker())
                .message(request.message().trim())
                .startedAt(toServiceTime(request.startedAt()))
                .endedAt(request.endedAt() == null ? null : toServiceTime(request.endedAt()))
                .edited(false)
                .build();

        return toResponse(talkLogRepository.save(talkLog), false);
    }

    private void validateCallStatus(VideoCallStatus status) {
        if (status != VideoCallStatus.IN_PROGRESS && status != VideoCallStatus.COMPLETED) {
            throw new GeneralException(GeneralErrorCode.TALK_LOG_INVALID_CALL_STATUS);
        }
    }

    private void validateTimeRange(OffsetDateTime startedAt, OffsetDateTime endedAt) {
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new GeneralException(GeneralErrorCode.TALK_LOG_INVALID_TIME_RANGE);
        }
    }

    private LocalDateTime toServiceTime(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(SERVICE_ZONE).toLocalDateTime();
    }

    private AiTalkLogResDTO.Saved toResponse(TalkLog talkLog, boolean duplicated) {
        return new AiTalkLogResDTO.Saved(
                talkLog.getId(),
                UUID.fromString(talkLog.getEventId()),
                duplicated
        );
    }
}
