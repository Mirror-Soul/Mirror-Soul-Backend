package com.mirrorsoul.mirrorsoul_api.dto.jobverification;

import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import java.time.LocalDateTime;

public record JobVerificationDecisionResponse(
        Long requestId,
        JobVerificationRequestStatus status,
        LocalDateTime reviewedAt,
        String rejectionReason
) {
}
