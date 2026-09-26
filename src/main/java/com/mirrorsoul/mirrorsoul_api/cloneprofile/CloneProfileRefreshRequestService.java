package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileGenerationInput;
import com.mirrorsoul.mirrorsoul_api.config.GeminiGenerationProperties;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CloneProfileRefreshRequestService {
    private final CloneRepository cloneRepository;
    private final CloneProfileGenerationJobRepository jobRepository;
    private final CloneProfileSourceLoader sourceLoader;
    private final CloneProfileSourceHasher sourceHasher;
    private final GeminiGenerationProperties generationProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> request(UUID userUuid, CloneProfileTrigger trigger) {
        if (!generationProperties.isEnabled()) return Optional.empty();

        Clone clone = cloneRepository.findByUserUuidForUpdate(userUuid)
                .orElseThrow(() -> new CloneProfileSourceException("Clone was not found"));
        String promptVersion = generationProperties.getPromptVersion();
        CloneProfileGenerationInput source = sourceLoader.load(userUuid, promptVersion);
        String sourceHash = sourceHasher.hash(source);
        if (sourceHash.equals(clone.getProfileSourceHash())) return Optional.empty();

        CloneProfileGenerationJob job = jobRepository
                .findFirstByCloneIdAndStatusOrderByCreatedAtAsc(
                        clone.getId(), CloneProfileGenerationJobStatus.PENDING)
                .map(existing -> {
                    existing.replacePendingRequest(trigger, sourceHash, promptVersion);
                    return existing;
                })
                .orElseGet(() -> jobRepository.save(
                        CloneProfileGenerationJob.create(clone, trigger, sourceHash, promptVersion)));
        jobRepository.flush();
        eventPublisher.publishEvent(new CloneProfileRefreshRequestedEvent(job.getId()));
        return Optional.of(job.getId());
    }
}
