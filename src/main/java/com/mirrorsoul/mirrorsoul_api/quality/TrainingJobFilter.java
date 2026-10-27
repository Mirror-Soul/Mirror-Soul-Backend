package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow;
import java.time.LocalDateTime;
import java.util.Set;

public record TrainingJobFilter(TrainingId.Kind kind, Long memberId, String status,
        AdminListWindow window, boolean unresolvedOnly, LocalDateTime stalledBefore) {
    public TrainingJobFilter {
        if (window == null || status != null && !Set.of("PENDING", "PROCESSING", "COMPLETED", "FAILED", "STALE").contains(status))
            throw QualitySupport.invalid();
    }
}
