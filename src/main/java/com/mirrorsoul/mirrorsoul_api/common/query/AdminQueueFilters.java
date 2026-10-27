package com.mirrorsoul.mirrorsoul_api.common.query;

import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyReport;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.ReportState;
import com.mirrorsoul.mirrorsoul_api.support.SupportTicket;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.Status;
import org.springframework.data.jpa.domain.Specification;

public final class AdminQueueFilters {
    private AdminQueueFilters() {}
    public static Specification<JobVerificationRequest> pendingDocuments(AdminListWindow window) {
        return window.<JobVerificationRequest>specification().and(
                (root, query, cb) -> cb.equal(root.get("status"), JobVerificationRequestStatus.PENDING));
    }
    public static Specification<SafetyReport> reports(AdminListWindow window, boolean unresolvedOnly) {
        return window.<SafetyReport>specification().and((root, query, cb) -> unresolvedOnly
                ? cb.notEqual(root.get("state"), ReportState.DECIDED) : cb.conjunction());
    }
    public static Specification<SupportTicket> support(AdminListWindow window, boolean awaitingReply) {
        return window.<SupportTicket>specification().and((root, query, cb) -> awaitingReply
                ? root.get("status").in(Status.RECEIVED, Status.IN_PROGRESS) : cb.conjunction());
    }
}
