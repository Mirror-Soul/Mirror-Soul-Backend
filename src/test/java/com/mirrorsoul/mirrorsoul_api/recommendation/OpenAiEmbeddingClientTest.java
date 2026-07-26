package com.mirrorsoul.mirrorsoul_api.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mirrorsoul.mirrorsoul_api.config.OpenAiEmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiEmbeddingClientTest {

    private MockRestServiceServer server;
    private OpenAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.com");
        server = MockRestServiceServer.bindTo(builder).build();

        OpenAiEmbeddingProperties properties = new OpenAiEmbeddingProperties();
        client = new OpenAiEmbeddingClient(builder.build(), properties);
    }

    @Test
    void requests1536DimensionEmbedding() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "input": "사용자 자기소개",
                          "model": "text-embedding-3-small",
                          "dimensions": 1536,
                          "encoding_format": "float"
                        }
                        """))
                .andRespond(withSuccess(
                        embeddingResponse(1536),
                        MediaType.APPLICATION_JSON
                ));

        float[] embedding = client.embed("사용자 자기소개");

        assertEquals(1536, embedding.length);
        server.verify();
    }

    @Test
    void rejectsUnexpectedResponseDimension() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withSuccess(
                        embeddingResponse(10),
                        MediaType.APPLICATION_JSON
                ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.embed("사용자 자기소개")
        );

        assertEquals(
                "OpenAI embedding dimension must be 1536, but was 10",
                exception.getMessage()
        );
        server.verify();
    }

    @Test
    void rejectsBlankInputWithoutCallingApi() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.embed("  ")
        );

        assertEquals("Embedding input must not be blank", exception.getMessage());
        server.verify();
    }

    private String embeddingResponse(int dimensions) {
        StringBuilder values = new StringBuilder(dimensions * 2);
        for (int index = 0; index < dimensions; index++) {
            if (index > 0) {
                values.append(',');
            }
            values.append('0');
        }

        return "{\"data\":[{\"embedding\":[%s],\"index\":0}]}"
                .formatted(values);
    }
}
