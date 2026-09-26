package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileGenerationInput;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloneProfileGenerationWorker {
    private final CloneProfileGenerationJobService jobService;
    private final CloneProfileSourceLoader sourceLoader;
    private final CloneProfileSourceHasher sourceHasher;
    private final ObjectProvider<CloneProfileGenerator> generatorProvider;
    private final CloneProfileValidator validator;
    private final CloneProfileWriter writer;

    public void execute(Long jobId) {
        jobService.start(jobId).ifPresent(job -> {
            try {
                CloneProfileGenerationInput source = sourceLoader.load(job.userUuid(), job.promptVersion());
                String currentHash = sourceHasher.hash(source);
                if (!job.sourceHash().equals(currentHash)) {
                    jobService.markStale(jobId);
                    return;
                }
                CloneProfileGenerator generator = generatorProvider.getIfAvailable();
                if (generator == null) {
                    throw new CloneProfileGenerationException(
                            "GENERATION_DISABLED", "Gemini generation is disabled", false);
                }
                GeneratedCloneProfile generated = validator.validate(generator.generate(source));
                writer.updateIfCurrent(jobId, job.userUuid(), currentHash, generated);
            } catch (RuntimeException exception) {
                log.warn("Clone profile generation failed. jobId={}, errorCode={}",
                        jobId, exception instanceof CloneProfileGenerationException known
                                ? known.getErrorCode() : "UNEXPECTED_ERROR");
                jobService.handleFailure(jobId, exception);
            }
        });
    }
}
