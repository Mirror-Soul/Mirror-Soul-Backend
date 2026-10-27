package com.mirrorsoul.mirrorsoul_api.jobreview;

import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobReviewExtensionService {
    private final JobVerificationRequestRepository requests;

    public record History(Long requestId, Long version, Long reviewerId,
            JobVerificationRequestStatus status, LocalDateTime submittedAt, LocalDateTime reviewedAt,
            com.mirrorsoul.mirrorsoul_api.domain.enums.Job claimedJob, String rejectionReason) {}
    public record Lifecycle(Long requestId, Long version, LocalDateTime evidenceExpiresAt,
            String evidenceState, LocalDateTime evidenceDeletedAt, int deletionAttempts,
            String deletionError, String notificationState, int notificationAttempts,
            LocalDateTime notificationUpdatedAt, String notificationError) {}

    @Transactional(readOnly = true)
    public Page<History> history(JobVerificationRequestStatus status, Long reviewerId,
            LocalDateTime from, LocalDateTime to, Long requestId, Long version, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || status == JobVerificationRequestStatus.PENDING
                || (from != null && to != null && from.isAfter(to))) invalid();
        Specification<JobVerificationRequest> filter = (root, query, cb) -> {
            var terms = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            terms.add(root.get("status").in(JobVerificationRequestStatus.APPROVED, JobVerificationRequestStatus.REJECTED));
            if (status != null) terms.add(cb.equal(root.get("status"), status));
            if (reviewerId != null) terms.add(cb.equal(root.get("reviewer").get("id"), reviewerId));
            if (from != null) terms.add(cb.greaterThanOrEqualTo(root.get("reviewedAt"), from));
            if (to != null) terms.add(cb.lessThanOrEqualTo(root.get("reviewedAt"), to));
            if (requestId != null) terms.add(cb.equal(root.get("id"), requestId));
            if (version != null) terms.add(cb.equal(root.get("version"), version));
            return cb.and(terms.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return requests.findAll(filter, PageRequest.of(page, size,
                Sort.by("reviewedAt").descending().and(Sort.by("id").descending())))
                .map(r -> new History(r.getId(), r.getVersion(), r.getReviewer() == null ? null : r.getReviewer().getId(),
                        r.getStatus(), r.getCreatedAt(), r.getReviewedAt(), r.getClaimedJob(), r.getRejectionReason()));
    }

    @Transactional(readOnly = true)
    public Lifecycle lifecycle(Long id) { return view(find(id)); }

    @Transactional
    public Lifecycle retryNotification(Long id, Long version, boolean possibleDuplicateAcknowledged) {
        var r = requests.findByIdForUpdate(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_VERIFICATION_REQUEST_NOT_FOUND));
        if (version == null || !version.equals(r.getVersion()) || r.getStatus() == JobVerificationRequestStatus.PENDING
                || !java.util.Set.of("FAILED", "UNKNOWN", "DISABLED", "NO_DEVICE").contains(r.getNotificationState())) invalid();
        if (java.util.Set.of("FAILED", "UNKNOWN").contains(r.getNotificationState()) && !possibleDuplicateAcknowledged) invalid();
        r.notification("PENDING", null, LocalDateTime.now());
        requests.flush();
        return view(r);
    }

    private JobVerificationRequest find(Long id) { return requests.findById(id)
            .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_VERIFICATION_REQUEST_NOT_FOUND)); }
    private static void invalid() { throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER); }
    private Lifecycle view(JobVerificationRequest r) {
        return new Lifecycle(r.getId(), r.getVersion(), r.getEvidenceExpiresAt(),
                r.getEvidenceDeletedAt() != null ? "DELETED" : r.getEvidenceExpiresAt().isAfter(LocalDateTime.now()) ? "AVAILABLE" : "EXPIRED",
                r.getEvidenceDeletedAt(), r.getEvidenceDeleteAttempts(), r.getEvidenceDeleteError(),
                r.getNotificationState(), r.getNotificationAttempts(), r.getNotificationUpdatedAt(), r.getNotificationError());
    }
}
