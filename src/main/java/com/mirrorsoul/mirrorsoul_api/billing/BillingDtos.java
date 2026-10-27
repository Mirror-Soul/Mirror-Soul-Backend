package com.mirrorsoul.mirrorsoul_api.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

public final class BillingDtos {
    private BillingDtos() {}
    public record Verify(@NotNull Store store, @NotBlank @Size(max = 4096) String reference) {
        @Override public String toString() { return "Verify[REDACTED]"; }
    }
    public record AppleNotification(@NotBlank @Size(max = 100000) String signedPayload) {}
    public record GoogleMessage(@NotBlank @Size(max = 100000) String data,
                                @NotBlank @Size(max = 200) String messageId) {}
    public record GooglePush(@NotNull @Valid GoogleMessage message) {}
    public record Adjustment(@NotNull @Positive Long memberId, @NotNull Integer deltaSeconds,
                             @NotBlank @Size(max = 500) String reason,
                             @NotBlank @Size(max = 100) String idempotencyKey) {}
    public record Retry(@NotBlank @Size(max = 100) String idempotencyKey) {}
    public record PaymentView(Long id, Store store, String transactionId, Long memberId, String productId,
                              Verification verificationStatus, Grant grantStatus, Refund refundStatus,
                              Consumption consumptionStatus, int grantedSeconds, int revokedSeconds,
                              List<Long> ledgerIds, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record LedgerView(Long id, Long memberId, String reason, int deltaSeconds, int balanceAfterSeconds,
                             Long paymentId, String transactionId, Long callId, Long actorId,
                             String memo, LocalDateTime createdAt) {}
    public record Balance(int balanceSeconds, int availableSeconds) {}
    public record JobView(Long id, Long paymentId, Action action, JobState state, int attempts,
                          String failureCode, LocalDateTime nextAttemptAt, LocalDateTime leaseUntil) {
        static JobView from(BillingJob j) {
            return new JobView(j.getId(), j.getPayment() == null ? null : j.getPayment().getId(),
                    j.getAction(), j.getState(), j.getAttempts(), j.getFailureCode(), j.getNextAttemptAt(), j.getLeaseUntil());
        }
    }
}
