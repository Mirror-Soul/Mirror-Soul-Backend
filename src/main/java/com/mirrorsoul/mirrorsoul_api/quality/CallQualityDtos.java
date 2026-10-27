package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class CallQualityDtos {
    private CallQualityDtos() {}
    public enum Step { STT_ENDED, LLM_FIRST_TOKEN, TTS_FIRST_AUDIO, VIDEO_READY, CLIENT_DELIVERED, CONNECTION, ERROR }
    public enum Stage { STT_TO_LLM, LLM_TO_TTS, TTS_TO_VIDEO, VIDEO_TO_CLIENT, TTS_TO_CLIENT, STT_TO_CLIENT }
    public enum Connection { GOOD, FAIR, POOR, DISCONNECTED }
    public enum Error { AI_UNAVAILABLE, SIGNALING_DELIVERY_FAILED, STT_TIMEOUT, LLM_TIMEOUT, TTS_TIMEOUT,
        VIDEO_TIMEOUT, NETWORK_ERROR, AUTHORIZATION_FAILED, INTERNAL_ERROR }
    public record Link(@NotNull Connection quality,
            @DecimalMin("0") @DecimalMax("100") Double packetLossPercent,
            @DecimalMin("0") @DecimalMax("60000") Double jitterMs,
            @DecimalMin("0") @DecimalMax("60000") Double roundTripMs,
            @DecimalMin("0") @DecimalMax("1000000") Double bitrateKbps) {}
    public record Event(@NotBlank @Size(max=100) String eventId,
            @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String turnId,
            @NotNull Step step,@NotNull OffsetDateTime occurredAt,@NotNull UUID correlationId,
            @NotNull @Positive Long cloneId,@NotNull @PositiveOrZero Long cloneVersion,
            @Valid Link connection,Error errorCode) {}
    public record Received(Long callId,boolean duplicate) {}
    public record Sample(String turnKey,Stage stage,long latencyMs,LocalDateTime measuredAt) {}
    public record Percentiles(Stage stage,long sampleCount,Long p50Ms,Long p95Ms) {}
    public record Window(String scope,OffsetDateTime from,OffsetDateTime to,Long callId,Long cloneId,
            Long cloneVersion,CallMediaType mediaType,List<Percentiles> stages) {}
    public record Signal(String code,LocalDateTime occurredAt) {}
    public record LedgerRef(Long id,String reason,int deltaSeconds,int balanceAfterSeconds) {}
    public record Diagnostics(Long callId,Long memberId,Long cloneId,Long cloneVersion,UUID correlationId,
            List<String> trainingJobIds,CallMediaType mediaType,VideoCallStatus status,
            String endReason,String endSource,LocalDateTime createdAt,LocalDateTime connectedAt,
            LocalDateTime endedAt,Link connection,List<Signal> errors,List<LedgerRef> ledger,
            long eventCount,long invalidStagePairs,long incompleteTurns,List<Sample> measurements,
            boolean measurementsTruncated,boolean errorsTruncated,Window distribution) {}
}
