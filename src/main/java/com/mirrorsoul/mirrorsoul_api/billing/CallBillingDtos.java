package com.mirrorsoul.mirrorsoul_api.billing;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;

public final class CallBillingDtos {
    private CallBillingDtos() {}
    public enum Kind { STARTED, ENDED }
    public enum EndReason { USER_HANGUP, AI_HANGUP, TIME_EXHAUSTED, NETWORK_ERROR, CANCELLED, SERVER_TIMEOUT }
    public record Event(@NotBlank @Size(max = 100) String eventId, @NotNull Kind kind,
                        @NotNull OffsetDateTime occurredAt, EndReason endReason) {}
    public record Result(Long callId, VideoCallStatus status, int availableSeconds,
                         Integer billingLimitSeconds, LocalDateTime billingDeadlineAt) {}
}
