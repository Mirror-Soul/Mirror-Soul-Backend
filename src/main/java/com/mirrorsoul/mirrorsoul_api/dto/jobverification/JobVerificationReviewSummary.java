package com.mirrorsoul.mirrorsoul_api.dto.jobverification;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobVerificationReviewSummary(
        Long requestId,
        UUID userUuid,
        Job claimedJob,
        LocalDateTime submittedAt
) {
}
