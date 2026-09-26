package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.config.CloneProfileGenerationProperties;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CloneProfileGenerationJobService {
    private final CloneProfileGenerationJobRepository jobRepository;
    private final CloneProfileGenerationProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<JobSnapshot> start(Long jobId) {
        CloneProfileGenerationJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (job == null || job.getStatus() != CloneProfileGenerationJobStatus.PENDING
                || (job.getNextAttemptAt() != null && job.getNextAttemptAt().isAfter(now))) {
            return Optional.empty();
        }
        if (job.getSourceHash().equals(job.getClone().getProfileSourceHash())) {
            job.complete(now);
            return Optional.empty();
        }
        job.start(now);
        return Optional.of(new JobSnapshot(
                job.getId(), job.getClone().getUser().getUuid(), job.getSourceHash(),
                job.getPromptVersion(), job.getAttemptCount()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStale(Long jobId) {
        jobRepository.findByIdForUpdate(jobId).ifPresent(job -> job.markStale(LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(Long jobId, RuntimeException exception) {
        jobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
            CloneProfileGenerationException known = exception instanceof CloneProfileGenerationException value
                    ? value : null;
            String code = known == null ? "UNEXPECTED_ERROR" : known.getErrorCode();
            boolean retryable = known == null || known.isRetryable();
            int maxAttempts = "INVALID_GENERATED_PROFILE".equals(code)
                    ? Math.min(2, properties.getMaxAttempts()) : properties.getMaxAttempts();
            if (retryable && job.getAttemptCount() < maxAttempts) {
                long baseSeconds = job.getAttemptCount() == 1 ? 5L : 30L;
                long jitter = ThreadLocalRandom.current().nextLong(1, 6);
                job.retryAt(LocalDateTime.now().plusSeconds(baseSeconds + jitter), code, safeMessage(exception));
            } else {
                job.fail(LocalDateTime.now(), code, safeMessage(exception));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverAbandoned(Long jobId) {
        jobRepository.findByIdForUpdate(jobId).ifPresent(job -> job.recover(LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> findDueJobs() {
        return jobRepository.findDueJobIds(LocalDateTime.now(), PageRequest.of(0, 20));
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> findAbandonedJobs() {
        return jobRepository.findAbandonedJobIds(
                LocalDateTime.now().minusMinutes(5), PageRequest.of(0, 20));
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public record JobSnapshot(Long jobId, java.util.UUID userUuid, String sourceHash,
                              String promptVersion, int attemptCount) {}
}
