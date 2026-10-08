package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserRole;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationDecisionResponse;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationReviewDetail;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationReviewSummary;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationStatusResponse;
import com.mirrorsoul.mirrorsoul_api.event.JobVerificationReviewedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobVerificationReviewService {

    private final JobVerificationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<JobVerificationReviewSummary> pending(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,
                    "page must be nonnegative and size must be between 1 and 100.");
        }
        return requestRepository.findByStatus(JobVerificationRequestStatus.PENDING,
                PageRequest.of(page, size, Sort.by("createdAt").ascending().and(Sort.by("id").ascending())))
                .map(request -> new JobVerificationReviewSummary(
                        request.getId(), request.getUser().getUuid(),
                        request.getClaimedJob(), request.getCreatedAt()
                ));
    }

    @Transactional(readOnly = true)
    public JobVerificationReviewDetail detail(Long requestId) {
        JobVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_VERIFICATION_REQUEST_NOT_FOUND));
        UUID userUuid = request.getUser().getUuid();
        var photos = request.getFiles().stream().map(file -> {
            var access = fileService.createJobVerificationReviewAccess(
                        userUuid, file.getBucket(), file.getObjectKey(),
                        file.getObjectVersionId(), file.getObjectEtag()
                );
            return new JobVerificationReviewDetail.Photo(
                    file.getId(), file.getDisplayOrder(), access.url(), access.signedHeaders()
            );
        }).toList();
        return new JobVerificationReviewDetail(
                request.getId(), userUuid, request.getClaimedJob(),
                request.getStatus(), request.getCreatedAt(), request.getReviewedAt(),
                request.getRejectionReason(), photos
        );
    }

    @Transactional
    public JobVerificationDecisionResponse approve(UUID reviewerUuid, Long requestId) {
        return decide(reviewerUuid, requestId, null);
    }

    @Transactional
    public JobVerificationDecisionResponse reject(UUID reviewerUuid, Long requestId, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,
                    "A rejection reason of at most 500 characters is required.");
        }
        return decide(reviewerUuid, requestId, reason.trim());
    }

    private JobVerificationDecisionResponse decide(UUID reviewerUuid, Long requestId, String reason) {
        User reviewer = userRepository.findByUuid(reviewerUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if (reviewer.getRole() != UserRole.ADMIN || reviewer.getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        JobVerificationRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_VERIFICATION_REQUEST_NOT_FOUND));
        if (request.getStatus() != JobVerificationRequestStatus.PENDING) {
            throw new GeneralException(GeneralErrorCode.JOB_VERIFICATION_ALREADY_REVIEWED);
        }
        if (request.getUser().getId().equals(reviewer.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN,
                    "Administrators cannot review their own job verification request.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (reason == null) {
            request.approve(reviewer, now);
        } else {
            request.reject(reviewer, reason, now);
        }
        events.publishEvent(new JobVerificationReviewedEvent(
                request.getId(), request.getUser().getUuid(), request.getStatus()
        ));
        return new JobVerificationDecisionResponse(
                request.getId(), request.getStatus(), request.getReviewedAt(), request.getRejectionReason()
        );
    }

    @Transactional(readOnly = true)
    public JobVerificationStatusResponse myLatest(UUID userUuid) {
        return requestRepository.findFirstByUser_UuidOrderByCreatedAtDescIdDesc(userUuid)
                .map(request -> new JobVerificationStatusResponse(
                        request.getId(), request.getStatus(), request.getClaimedJob(),
                        request.getCreatedAt(), request.getReviewedAt(), request.getRejectionReason(),
                        request.getClaimedJob() == request.getUser().getJob(),
                        request.getStatus() == JobVerificationRequestStatus.APPROVED
                                && request.getClaimedJob() == request.getUser().getJob()
                ))
                .orElseGet(() -> new JobVerificationStatusResponse(
                        null, null, null, null, null, null, false, false
                ));
    }
}
