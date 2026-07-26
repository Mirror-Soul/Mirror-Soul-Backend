package com.mirrorsoul.mirrorsoul_api.recommendation;

import com.mirrorsoul.mirrorsoul_api.config.GeminiEmbeddingProperties;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "gemini.embedding",
        name = "enabled",
        havingValue = "true"
)
public class GeminiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final GeminiEmbeddingProperties properties;

    public GeminiEmbeddingClient(
            @Qualifier("geminiRestClient") RestClient restClient,
            GeminiEmbeddingProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public float[] embed(String input) {
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("Embedding input must not be blank");
        }

        String model = properties.getModel();
        EmbeddingResponse response = restClient.post()
                .uri("/v1beta/models/{model}:embedContent", model)
                .body(Map.of(
                        "model", "models/" + model,
                        "content", Map.of(
                                "parts", List.of(Map.of("text", input))
                        ),
                        "taskType", properties.getTaskType(),
                        "outputDimensionality", properties.getDimensions()
                ))
                .retrieve()
                .body(EmbeddingResponse.class);

        float[] embedding = extractEmbedding(response);
        validateEmbedding(embedding);
        return embedding;
    }

    private float[] extractEmbedding(EmbeddingResponse response) {
        if (response == null
                || response.embedding() == null
                || response.embedding().values() == null) {
            throw new IllegalStateException("Gemini returned an empty embedding response");
        }
        return response.embedding().values();
    }

    private void validateEmbedding(float[] embedding) {
        int actualDimensions = embedding.length;
        if (actualDimensions != properties.getDimensions()) {
            throw new IllegalStateException(
                    "Gemini embedding dimension must be %d, but was %d"
                            .formatted(properties.getDimensions(), actualDimensions)
            );
        }

        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException(
                        "Gemini embedding contains a non-finite value"
                );
            }
        }
    }

    private record EmbeddingResponse(ContentEmbedding embedding) {
    }

    private record ContentEmbedding(float[] values) {
    }
}
