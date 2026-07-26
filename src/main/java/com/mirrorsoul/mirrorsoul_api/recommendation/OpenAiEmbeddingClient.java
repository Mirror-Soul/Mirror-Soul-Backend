package com.mirrorsoul.mirrorsoul_api.recommendation;

import com.mirrorsoul.mirrorsoul_api.config.OpenAiEmbeddingProperties;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "openai.embedding",
        name = "enabled",
        havingValue = "true"
)
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final OpenAiEmbeddingProperties properties;

    public OpenAiEmbeddingClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiEmbeddingProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public float[] embed(String input) {
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("Embedding input must not be blank");
        }

        EmbeddingResponse response = restClient.post()
                .uri("/v1/embeddings")
                .body(Map.of(
                        "input", input,
                        "model", properties.getModel(),
                        "dimensions", properties.getDimensions(),
                        "encoding_format", "float"
                ))
                .retrieve()
                .body(EmbeddingResponse.class);

        float[] embedding = extractEmbedding(response);
        validateEmbedding(embedding);
        return embedding;
    }

    private float[] extractEmbedding(EmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenAI returned an empty embedding response");
        }
        return response.data().get(0).embedding();
    }

    private void validateEmbedding(float[] embedding) {
        int actualDimensions = embedding == null ? 0 : embedding.length;
        if (actualDimensions != properties.getDimensions()) {
            throw new IllegalStateException(
                    "OpenAI embedding dimension must be %d, but was %d"
                            .formatted(properties.getDimensions(), actualDimensions)
            );
        }

        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException(
                        "OpenAI embedding contains a non-finite value"
                );
            }
        }
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(float[] embedding, int index) {
    }
}
