package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.dto.RagProfileRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "rag.profile", name = "enabled", havingValue = "true")
public class RagProfileClient {
    private final RestClient client;

    public RagProfileClient(@Qualifier("ragProfileRestClient") RestClient client) {
        this.client = client;
    }

    public void send(RagProfileRequest request) {
        var response = client.post().uri("/api/v1/training/profiles")
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .retrieve().toEntity(ProfileResponse.class);
        var body = response.getBody();
        if (response.getStatusCode().value() != 200 || body == null
                || !Boolean.TRUE.equals(body.success()) || !"stored".equals(body.status())
                || body.documentId() == null || body.documentId().isBlank()) {
            throw new IllegalStateException("Invalid RAG profile success response");
        }
    }

    public record ProfileResponse(Boolean success, String documentId, String status) {}
}
