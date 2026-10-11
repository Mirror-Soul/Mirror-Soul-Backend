package com.mirrorsoul.mirrorsoul_api.quality;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class TrainingDtos {
    private TrainingDtos() {}
    public enum State { CREATED, STARTED, PROGRESS, COMPLETED, FAILED }
    public enum Stage { QUEUED, DISPATCHING, PREPARING, TRAINING, VALIDATING, ACTIVATING, COMPLETED }
    public record Trace(String trainingJobId, Long cloneId, Long cloneVersion, UUID correlationId, int retryCount) {}
    public record Lifecycle(@NotBlank @Size(max=100) String eventId, @NotNull @PositiveOrZero Long sequence,
            @NotNull State state, @NotNull Stage stage, @NotNull OffsetDateTime occurredAt,
            @NotNull @Positive Long cloneId, @NotNull @PositiveOrZero Long cloneVersion,
            @NotNull UUID correlationId, QualitySupport.Failure failureCode, Boolean retryAllowed) {}
    public record Received(String trainingJobId, boolean duplicate, boolean ignored) {}
    public record Retry(@NotBlank @Size(max=100) String idempotencyKey) {}
    public record Job(String trainingJobId, TrainingId.Kind kind, Long sourceJobId, Long memberId, Long cloneId,
            Long cloneVersion, UUID correlationId, Stage stage, String status, QualitySupport.Failure failureCode,
            boolean retryAllowed, int retryCount, String retriedFromJobId, LocalDateTime createdAt,
            LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime updatedAt) {}
    public record RetryResult(String trainingJobId, String newTrainingJobId, int retryCount) {}
}
