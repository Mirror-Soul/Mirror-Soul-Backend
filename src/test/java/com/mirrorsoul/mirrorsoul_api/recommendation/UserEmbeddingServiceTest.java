package com.mirrorsoul.mirrorsoul_api.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@SuppressWarnings("unchecked")
class UserEmbeddingServiceTest {

    private final EmbeddingTextBuilder textBuilder = mock(EmbeddingTextBuilder.class);
    private final EmbeddingSourceHasher sourceHasher = new EmbeddingSourceHasher();
    private final EmbeddingClient client = mock(EmbeddingClient.class);
    private final UserEmbeddingRepository repository = mock(UserEmbeddingRepository.class);
    private final ObjectProvider<EmbeddingClient> clientProvider = mock(ObjectProvider.class);
    private final ObjectProvider<UserEmbeddingRepository> repositoryProvider = mock(ObjectProvider.class);
    private UserEmbeddingService service;

    @BeforeEach
    void setUp() {
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(repositoryProvider.getIfAvailable()).thenReturn(repository);
        service = new UserEmbeddingService(
                textBuilder,
                sourceHasher,
                clientProvider,
                repositoryProvider
        );
    }

    @Test
    void skipsEmbeddingWhenSourceHashHasNotChanged() {
        UUID userUuid = UUID.randomUUID();
        String source = "사용자 자기소개:\n\n백엔드 개발자입니다.";
        String sourceHash = sourceHasher.hash(source);
        when(textBuilder.build(userUuid, EmbeddingType.PROFILE)).thenReturn(source);
        when(repository.findSourceHash(userUuid, EmbeddingType.PROFILE))
                .thenReturn(Optional.of(sourceHash));

        service.generate(userUuid, EmbeddingType.PROFILE);

        verify(client, never()).embed(source);
        verify(repository, never()).upsert(
                eq(userUuid),
                eq(EmbeddingType.PROFILE),
                any(float[].class),
                anyString()
        );
    }

    @Test
    void embedsAndUpsertsChangedSource() {
        UUID userUuid = UUID.randomUUID();
        String source = "직업 분야: 기술 및 IT\n직무 설명: Spring Boot 백엔드 개발자";
        String sourceHash = sourceHasher.hash(source);
        float[] embedding = new float[1536];
        when(textBuilder.build(userUuid, EmbeddingType.JOB)).thenReturn(source);
        when(repository.findSourceHash(userUuid, EmbeddingType.JOB))
                .thenReturn(Optional.empty());
        when(client.embed(source)).thenReturn(embedding);

        service.generate(userUuid, EmbeddingType.JOB);

        verify(client).embed(source);
        verify(repository).upsert(userUuid, EmbeddingType.JOB, embedding, sourceHash);
    }
}
