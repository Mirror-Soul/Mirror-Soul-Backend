package com.mirrorsoul.mirrorsoul_api.recommendation;

import java.util.List;
import java.util.UUID;

public interface UserEmbeddingRepository {

    void upsert(UUID userUuid, EmbeddingType type, float[] embedding, String sourceHash);

    List<VectorSimilarityScores> findSimilarityScores(
            UUID requesterUuid,
            List<UUID> candidateUuids
    );

    void deleteByUserUuid(UUID userUuid);
}
