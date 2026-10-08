package com.mirrorsoul.mirrorsoul_api.event;

import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import java.util.UUID;

public record JobVerificationReviewedEvent(
        Long requestId,
        UUID userUuid,
        JobVerificationRequestStatus status
) {
}
