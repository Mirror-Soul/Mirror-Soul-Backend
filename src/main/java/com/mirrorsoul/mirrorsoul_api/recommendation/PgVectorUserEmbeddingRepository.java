package com.mirrorsoul.mirrorsoul_api.recommendation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "vector.datasource", name = "enabled", havingValue = "true")
public class PgVectorUserEmbeddingRepository implements UserEmbeddingRepository {

    private static final int EMBEDDING_DIMENSION = 1536;
    private static final String PROVIDER = "QWEN";
    private static final String MODEL = "Qwen/Qwen3-Embedding-8B";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PgVectorUserEmbeddingRepository(
            @Qualifier("vectorJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(
            UUID userUuid,
            EmbeddingType type,
            float[] embedding,
            String sourceHash
    ) {
        validateEmbedding(embedding);

        String embeddingColumn = type.embeddingColumn();
        String sourceHashColumn = type.sourceHashColumn();
        String sql = """
                INSERT INTO recommendation.user_embeddings (
                    user_uuid,
                    %s,
                    %s,
                    embedding_provider,
                    embedding_model,
                    embedding_dimension
                ) VALUES (
                    :userUuid,
                    CAST(:embedding AS vector),
                    :sourceHash,
                    :provider,
                    :model,
                    :dimension
                )
                ON CONFLICT (user_uuid) DO UPDATE SET
                    %s = EXCLUDED.%s,
                    %s = EXCLUDED.%s,
                    embedding_provider = EXCLUDED.embedding_provider,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dimension = EXCLUDED.embedding_dimension,
                    updated_at = NOW()
                """.formatted(
                embeddingColumn,
                sourceHashColumn,
                embeddingColumn,
                embeddingColumn,
                sourceHashColumn,
                sourceHashColumn
        );

        jdbcTemplate.update(sql, Map.of(
                "userUuid", userUuid,
                "embedding", toVectorLiteral(embedding),
                "sourceHash", sourceHash,
                "provider", PROVIDER,
                "model", MODEL,
                "dimension", EMBEDDING_DIMENSION
        ));
    }

    @Override
    public List<VectorSimilarityScores> findSimilarityScores(
            UUID requesterUuid,
            List<UUID> candidateUuids
    ) {
        if (candidateUuids.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT
                    candidate.user_uuid,
                    CASE WHEN requester.job_embedding IS NULL OR candidate.job_embedding IS NULL
                        THEN NULL
                        ELSE GREATEST(0.0, 1.0 - (candidate.job_embedding <=> requester.job_embedding))
                    END AS job_score,
                    CASE WHEN requester.profile_embedding IS NULL OR candidate.profile_embedding IS NULL
                        THEN NULL
                        ELSE GREATEST(0.0, 1.0 - (candidate.profile_embedding <=> requester.profile_embedding))
                    END AS profile_score,
                    CASE WHEN requester.clone_summary_embedding IS NULL OR candidate.clone_summary_embedding IS NULL
                        THEN NULL
                        ELSE GREATEST(0.0, 1.0 - (candidate.clone_summary_embedding <=> requester.clone_summary_embedding))
                    END AS clone_summary_score,
                    CASE WHEN requester.conversation_embedding IS NULL OR candidate.conversation_embedding IS NULL
                        THEN NULL
                        ELSE GREATEST(0.0, 1.0 - (candidate.conversation_embedding <=> requester.conversation_embedding))
                    END AS conversation_score,
                    CASE WHEN requester.interview_embedding IS NULL OR candidate.interview_embedding IS NULL
                        THEN NULL
                        ELSE GREATEST(0.0, 1.0 - (candidate.interview_embedding <=> requester.interview_embedding))
                    END AS interview_score
                FROM recommendation.user_embeddings requester
                JOIN recommendation.user_embeddings candidate
                    ON candidate.user_uuid IN (:candidateUuids)
                WHERE requester.user_uuid = :requesterUuid
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "requesterUuid", requesterUuid,
                        "candidateUuids", candidateUuids
                ),
                (resultSet, rowNumber) -> new VectorSimilarityScores(
                        resultSet.getObject("user_uuid", UUID.class),
                        getNullableDouble(resultSet, "job_score"),
                        getNullableDouble(resultSet, "profile_score"),
                        getNullableDouble(resultSet, "clone_summary_score"),
                        getNullableDouble(resultSet, "conversation_score"),
                        getNullableDouble(resultSet, "interview_score")
                )
        );
    }

    @Override
    public void deleteByUserUuid(UUID userUuid) {
        jdbcTemplate.update(
                "DELETE FROM recommendation.user_embeddings WHERE user_uuid = :userUuid",
                Map.of("userUuid", userUuid)
        );
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIMENSION) {
            int actualDimension = embedding == null ? 0 : embedding.length;
            throw new IllegalArgumentException(
                    "Embedding dimension must be %d, but was %d"
                            .formatted(EMBEDDING_DIMENSION, actualDimension)
            );
        }

        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding contains a non-finite value");
            }
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 12).append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding[index]);
        }
        return builder.append(']').toString();
    }

    private Double getNullableDouble(java.sql.ResultSet resultSet, String columnName)
            throws java.sql.SQLException {
        double value = resultSet.getDouble(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
