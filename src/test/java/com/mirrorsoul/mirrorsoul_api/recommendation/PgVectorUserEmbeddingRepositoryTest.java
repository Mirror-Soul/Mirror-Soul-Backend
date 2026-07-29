package com.mirrorsoul.mirrorsoul_api.recommendation;

import com.mirrorsoul.mirrorsoul_api.config.GeminiEmbeddingProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PgVectorUserEmbeddingRepositoryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate =
            mock(NamedParameterJdbcTemplate.class);
    private final GeminiEmbeddingProperties embeddingProperties =
            new GeminiEmbeddingProperties();
    private final PgVectorUserEmbeddingRepository repository =
            new PgVectorUserEmbeddingRepository(jdbcTemplate, embeddingProperties);

    @Test
    void rejectsEmbeddingWithUnexpectedDimension() {
        UUID userUuid = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> repository.upsert(
                        userUuid,
                        EmbeddingType.PROFILE,
                        new float[1024],
                        "a".repeat(64)
                )
        );

        assertEquals(
                "Embedding dimension must be 1536, but was 1024",
                exception.getMessage()
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsEmbeddingContainingNonFiniteValue() {
        float[] embedding = new float[1536];
        embedding[100] = Float.NaN;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> repository.upsert(
                        UUID.randomUUID(),
                        EmbeddingType.INTERVIEW,
                        embedding,
                        "a".repeat(64)
                )
        );

        assertEquals("Embedding contains a non-finite value", exception.getMessage());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void skipsDatabaseWhenCandidateListIsEmpty() {
        List<VectorSimilarityScores> scores = repository.findSimilarityScores(
                UUID.randomUUID(),
                List.of()
        );

        assertEquals(List.of(), scores);
        verifyNoInteractions(jdbcTemplate);
    }
}
