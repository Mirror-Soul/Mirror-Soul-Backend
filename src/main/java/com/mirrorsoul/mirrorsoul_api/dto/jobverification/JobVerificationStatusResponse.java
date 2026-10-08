package com.mirrorsoul.mirrorsoul_api.dto.jobverification;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import java.time.LocalDateTime;

public record JobVerificationStatusResponse(
        Long requestId,
        JobVerificationRequestStatus status,
        Job claimedJob,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String rejectionReason,
        boolean appliesToCurrentJob,
        boolean passVerificationRequired
) {
}
