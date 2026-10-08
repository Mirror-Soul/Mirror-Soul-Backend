package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobVerificationDisplayService {

    private final JobVerificationRequestRepository requests;

    public boolean hasCurrentSubmission(User user) {
        return requests.findFirstByUser_UuidOrderByCreatedAtDescIdDesc(user.getUuid())
                .filter(request -> request.getClaimedJob() == user.getJob())
                .map(request -> request.getStatus() == JobVerificationRequestStatus.PENDING
                        || request.getStatus() == JobVerificationRequestStatus.APPROVED)
                .orElse(false);
    }

    public boolean documentReviewCompleted(User user) {
        return requests.findFirstByUser_UuidOrderByCreatedAtDescIdDesc(user.getUuid())
                .filter(request -> request.getClaimedJob() == user.getJob())
                .map(request -> request.getStatus() == JobVerificationRequestStatus.APPROVED)
                .orElse(false);
    }

    public Map<Long, Boolean> documentReviewCompletedFor(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> usersById = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return requests.findLatestForUserIds(usersById.keySet()).stream()
                .collect(Collectors.toMap(
                        request -> request.getUser().getId(),
                        request -> isApprovedForCurrentJob(request, usersById.get(request.getUser().getId()))
                ));
    }

    private boolean isApprovedForCurrentJob(JobVerificationRequest request, User user) {
        return request.getStatus() == JobVerificationRequestStatus.APPROVED
                && request.getClaimedJob() == user.getJob();
    }
}
