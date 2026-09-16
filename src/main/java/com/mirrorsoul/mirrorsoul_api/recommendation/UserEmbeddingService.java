package com.mirrorsoul.mirrorsoul_api.recommendation;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmbeddingService {

    private final EmbeddingTextBuilder textBuilder;
    private final EmbeddingSourceHasher sourceHasher;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final ObjectProvider<UserEmbeddingRepository> embeddingRepositoryProvider;

    public void generate(UUID userUuid, EmbeddingType type) {
        EmbeddingClient client = embeddingClientProvider.getIfAvailable();
        UserEmbeddingRepository repository = embeddingRepositoryProvider.getIfAvailable();
        if (client == null || repository == null) {
            log.debug("Embedding infrastructure is disabled. userUuid={}, type={}", userUuid, type);
            return;
        }

        String sourceText = textBuilder.build(userUuid, type);
        if (!StringUtils.hasText(sourceText)) {
            log.debug("Embedding source is empty. userUuid={}, type={}", userUuid, type);
            return;
        }

        String sourceHash = sourceHasher.hash(sourceText);
        if (repository.findSourceHash(userUuid, type)
                .filter(sourceHash::equals)
                .isPresent()) {
            return;
        }

        float[] embedding = client.embed(sourceText);
        repository.upsert(userUuid, type, embedding, sourceHash);
    }
}
