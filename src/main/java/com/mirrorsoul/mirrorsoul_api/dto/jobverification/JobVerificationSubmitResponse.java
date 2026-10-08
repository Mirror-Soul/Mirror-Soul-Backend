package com.mirrorsoul.mirrorsoul_api.dto.jobverification;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;

public record JobVerificationSubmitResponse(
        Long requestId,
        JobVerificationRequestStatus status,
        Job claimedJob
) {
}
