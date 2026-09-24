package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.mirrorsoul.mirrorsoul_api.config.RagProfileConfig;
import com.mirrorsoul.mirrorsoul_api.dto.RagProfileRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RagProfileClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RagProfileClient client = new RagProfileClient(builder.build());
    private final UUID uuid = UUID.randomUUID();

    private RagProfileRequest request() {
        return new RagProfileRequest(uuid, 14L, "clone-14", 24, "male", "INFP", "소개",
                List.of(), List.of(), List.of(new RagProfileRequest.InterviewSample(1L, "", "질문", "답변")), 12);
    }

    @Test
    void sendsUuidStableProfileAndTranscriptWithExpectedContract() {
        server.expect(requestTo("http://ai.test/api/v1/training/profiles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(uuid.toString()))
                .andExpect(jsonPath("$.cloneId").value(14))
                .andExpect(jsonPath("$.aiProfileId").value("clone-14"))
                .andExpect(jsonPath("$.interviewSamples[0].transcript").value("답변"))
                .andRespond(withSuccess("""
                        {"success":true,"documentId":"member_profile_1","status":"stored",
                         "keywords":["음악"],"profileSummary":"summary"}
                        """, MediaType.APPLICATION_JSON));
        client.send(request());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"success\":false}",
            "{\"success\":true,\"status\":\"queued\",\"documentId\":\"x\"}", "not-json"})
    void rejectsInvalidSuccessBodies(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.send(request())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void propagatesServerFailureForRetry() {
        server.expect(anything()).andRespond(withServerError());
        assertThatThrownBy(() -> client.send(request())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void propagatesTimeoutForRetry() {
        server.expect(anything()).andRespond(withException(new IOException("timeout")));
        assertThatThrownBy(() -> client.send(request())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void requiresServerUrlAndCallbackSecretWhenEnabled() {
        var config = new RagProfileConfig();
        assertThatThrownBy(() -> config.ragProfileRestClient("", "secret"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.ragProfileRestClient("http://ai.test", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
