package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.ClonePersonalityTag;
import com.mirrorsoul.mirrorsoul_api.event.UserEmbeddingRefreshRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingType;
import com.mirrorsoul.mirrorsoul_api.repository.ClonePersonalityTagRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CloneProfileWriter {
    private final CloneRepository cloneRepository;
    private final ClonePersonalityTagRepository tagRepository;
    private final CloneProfileGenerationJobRepository jobRepository;
    private final CloneProfileSourceLoader sourceLoader;
    private final CloneProfileSourceHasher sourceHasher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateIfCurrent(Long jobId, UUID userUuid, String requestedSourceHash,
                                   GeneratedCloneProfile generated) {
        Clone clone = cloneRepository.findByUserUuidForUpdate(userUuid)
                .orElseThrow(() -> new CloneProfileSourceException("Clone was not found"));
        CloneProfileGenerationJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new CloneProfileSourceException("Clone profile job was not found"));
        if (job.getStatus() != CloneProfileGenerationJobStatus.PROCESSING) return false;

        String latestHash = sourceHasher.hash(sourceLoader.load(userUuid, job.getPromptVersion()));
        if (!requestedSourceHash.equals(latestHash)) {
            job.markStale(LocalDateTime.now());
            return false;
        }

        clone.updateProfile(generated.summary(), requestedSourceHash);
        tagRepository.deleteAllByCloneId(clone.getId());
        tagRepository.flush();
        for (int index = 0; index < generated.personalityTags().size(); index++) {
            tagRepository.save(ClonePersonalityTag.create(
                    clone, generated.personalityTags().get(index), (byte) index));
        }
        job.complete(LocalDateTime.now());
        eventPublisher.publishEvent(
                new UserEmbeddingRefreshRequestedEvent(userUuid, EmbeddingType.CLONE_SUMMARY));
        return true;
    }
}
