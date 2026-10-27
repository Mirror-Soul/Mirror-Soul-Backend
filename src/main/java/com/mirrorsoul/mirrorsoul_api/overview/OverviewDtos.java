package com.mirrorsoul.mirrorsoul_api.overview;

import com.mirrorsoul.mirrorsoul_api.access.AdminPermission;
import com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class OverviewDtos {
    private OverviewDtos() {}
    public enum Key {
        PENDING_DOCUMENTS(AdminPermission.job_review),
        URGENT_REPORTS(AdminPermission.moderation_read),
        UNANSWERED_SUPPORT(AdminPermission.support_read),
        FAILED_TRAINING_JOBS(AdminPermission.clone_training_read),
        STALLED_TRAINING_JOBS(AdminPermission.clone_training_read);
        public final AdminPermission permission;
        Key(AdminPermission permission) { this.permission = permission; }
    }
    public enum DataState { READY, UNAVAILABLE }
    public enum State { COMPLETE, PARTIAL, UNAVAILABLE }
    public record Drilldown(String method, String path, Map<String,String> query, String href) {}
    public record Card(Key key, Long count, DataState dataState, OffsetDateTime checkedAt,
            long durationMs, String failureCode, Drilldown drilldown) {}
    public record Overview(OffsetDateTime asOf, OffsetDateTime generatedAt, AdminListWindow period,
            String periodBasis, String source, String consistency, State dataState, List<Card> cards) {}
}
