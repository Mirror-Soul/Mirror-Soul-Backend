package com.mirrorsoul.mirrorsoul_api.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mirrorsoul.mirrorsoul_api.config.GeminiEmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiEmbeddingClientTest {

    private MockRestServiceServer server;
    private GeminiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", "test-api-key");
        server = MockRestServiceServer.bindTo(builder).build();

        GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties();
        client = new GeminiEmbeddingClient(builder.build(), properties);
    }

    @Test
    void requests1536DimensionEmbedding() {
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                                + "gemini-embedding-001:embedContent"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andExpect(content().json("""
                        {
                          "model": "models/gemini-embedding-001",
                          "content": {
                            "parts": [
                              {"text": "사용자 자기소개"}
                            ]
                          },
                          "taskType": "SEMANTIC_SIMILARITY",
                          "outputDimensionality": 1536
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
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                                + "gemini-embedding-001:embedContent"
                ))
                .andRespond(withSuccess(
                        embeddingResponse(10),
                        MediaType.APPLICATION_JSON
                ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.embed("사용자 자기소개")
        );

        assertEquals(
                "Gemini embedding dimension must be 1536, but was 10",
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

        return "{\"embedding\":{\"values\":[%s]}}".formatted(values);
    }
}
