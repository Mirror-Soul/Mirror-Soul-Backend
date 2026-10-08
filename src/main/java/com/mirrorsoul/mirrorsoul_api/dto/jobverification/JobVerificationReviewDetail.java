package com.mirrorsoul.mirrorsoul_api.dto.jobverification;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobVerificationReviewDetail(
        Long requestId,
        UUID userUuid,
        Job claimedJob,
        JobVerificationRequestStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String rejectionReason,
        List<Photo> photos
) {
    public record Photo(Long fileId, int displayOrder, String reviewUrl,
                        Map<String, List<String>> signedHeaders) {
    }
}
