package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationSubmitResponse;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobVerificationRequestWriter {

    private final UserRepository userRepository;
    private final JobVerificationRequestRepository requestRepository;

    @Transactional
    public JobVerificationSubmitResponse createPending(
            UUID userUuid,
            List<JobVerificationRequest.FileSnapshot> snapshots
    ) {
        User user = userRepository.findByUuidForUpdate(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.DELETED) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        if (user.getJob() == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,
                    "A job must be selected before submitting a job verification request.");
        }

        List<JobVerificationRequest> pendingRequests = requestRepository
                .findByUser_IdAndStatus(user.getId(), JobVerificationRequestStatus.PENDING);
        if (!pendingRequests.isEmpty()) {
            JobVerificationRequest pending = pendingRequests.get(0);
            if (pendingRequests.size() == 1
                    && pending.matchesSubmission(user.getJob(), snapshots)) {
                return new JobVerificationSubmitResponse(pending.getId(), pending.getStatus(), pending.getClaimedJob());
            }
            throw new GeneralException(GeneralErrorCode.JOB_VERIFICATION_ALREADY_PENDING);
        }

        JobVerificationRequest verificationRequest =
                JobVerificationRequest.pending(user, user.getJob());
        for (int i = 0; i < snapshots.size(); i++) {
            JobVerificationRequest.FileSnapshot image = snapshots.get(i);
            verificationRequest.addFile(
                    image.bucket(), image.objectKey(), image.versionId(), image.etag(), i
            );
        }
        JobVerificationRequest saved = requestRepository.save(verificationRequest);
        return new JobVerificationSubmitResponse(saved.getId(), saved.getStatus(), saved.getClaimedJob());
    }
}
