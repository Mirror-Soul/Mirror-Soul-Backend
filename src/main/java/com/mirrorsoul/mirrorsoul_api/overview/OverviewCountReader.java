package com.mirrorsoul.mirrorsoul_api.overview;

import com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow;
import com.mirrorsoul.mirrorsoul_api.common.query.AdminQueueFilters;
import com.mirrorsoul.mirrorsoul_api.overview.OverviewDtos.Key;
import com.mirrorsoul.mirrorsoul_api.quality.*;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyReportRepository;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.Priority;
import com.mirrorsoul.mirrorsoul_api.support.SupportTicketRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class OverviewCountReader {
    private final JobVerificationRequestRepository documents;
    private final SafetyReportRepository reports;
    private final SupportTicketRepository support;
    private final TrainingReadService training;

    // A failed card rolls back its own read transaction, leaving other cards queryable.
    @Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW,
            timeoutString="${admin.overview.query-timeout-seconds:3}")
    public long count(Key key, AdminListWindow period, LocalDateTime stalledBefore) {
        return switch(key) {
            case PENDING_DOCUMENTS -> documents.count(AdminQueueFilters.pendingDocuments(period));
            case URGENT_REPORTS -> reports.count(AdminQueueFilters.reports(period,true).and(
                    (root, query, cb) -> cb.equal(root.get("priority"),Priority.URGENT)));
            case UNANSWERED_SUPPORT -> support.count(AdminQueueFilters.support(period,true));
            case FAILED_TRAINING_JOBS -> training.count(new TrainingJobFilter(null,null,"FAILED",period,true,null));
            case STALLED_TRAINING_JOBS -> training.count(new TrainingJobFilter(null,null,null,period,false,stalledBefore));
        };
    }
}
