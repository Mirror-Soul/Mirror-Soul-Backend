package com.mirrorsoul.mirrorsoul_api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.config.GeminiEmbeddingProperties;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingClient;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingTextBuilder;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingType;
import com.mirrorsoul.mirrorsoul_api.recommendation.UserEmbeddingRepository;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class UserEmbeddingServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final InterviewRecordRepository interviewRecordRepository =
            mock(InterviewRecordRepository.class);
    private final CloneRepository cloneRepository = mock(CloneRepository.class);
    private final EmbeddingTextBuilder textBuilder = mock(EmbeddingTextBuilder.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final UserEmbeddingRepository embeddingRepository =
            mock(UserEmbeddingRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider =
            mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserEmbeddingRepository> embeddingRepositoryProvider =
            mock(ObjectProvider.class);
    private final GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties();

    private final UserEmbeddingService service = new UserEmbeddingService(
            userRepository,
            interviewRecordRepository,
            cloneRepository,
            textBuilder,
            embeddingClientProvider,
            embeddingRepositoryProvider,
            properties
    );

    @Test
    void generatesAndStoresChangedProfileEmbedding() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        float[] embedding = new float[1536];
        when(embeddingClientProvider.getIfAvailable()).thenReturn(embeddingClient);
        when(embeddingRepositoryProvider.getIfAvailable()).thenReturn(embeddingRepository);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(textBuilder.buildProfileText(user)).thenReturn(Optional.of("자기소개:\n음악을 좋아합니다."));
        when(embeddingRepository.findSourceHash(userUuid, EmbeddingType.PROFILE))
                .thenReturn(Optional.empty());
        when(embeddingClient.embed(any())).thenReturn(embedding);

        service.refresh(userUuid, EmbeddingType.PROFILE);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingRepository).upsert(
                eq(userUuid),
                eq(EmbeddingType.PROFILE),
                eq(embedding),
                hashCaptor.capture()
        );
        assertEquals(64, hashCaptor.getValue().length());
    }

    @Test
    void skipsGeminiWhenSourceHashIsUnchanged() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        float[] embedding = new float[1536];
        when(embeddingClientProvider.getIfAvailable()).thenReturn(embeddingClient);
        when(embeddingRepositoryProvider.getIfAvailable()).thenReturn(embeddingRepository);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(textBuilder.buildProfileText(user)).thenReturn(Optional.of("자기소개:\n음악을 좋아합니다."));
        when(embeddingRepository.findSourceHash(userUuid, EmbeddingType.PROFILE))
                .thenReturn(Optional.empty());
        when(embeddingClient.embed(any())).thenReturn(embedding);

        service.refresh(userUuid, EmbeddingType.PROFILE);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingRepository).upsert(
                eq(userUuid),
                eq(EmbeddingType.PROFILE),
                eq(embedding),
                hashCaptor.capture()
        );
        when(embeddingRepository.findSourceHash(userUuid, EmbeddingType.PROFILE))
                .thenReturn(Optional.of(hashCaptor.getValue()));

        service.refresh(userUuid, EmbeddingType.PROFILE);

        verify(embeddingClient, times(1)).embed(any());
        verify(embeddingRepository, times(1)).upsert(
                eq(userUuid),
                eq(EmbeddingType.PROFILE),
                eq(embedding),
                any()
        );
    }

    @Test
    void skipsWorkWhenEmbeddingInfrastructureIsDisabled() {
        UUID userUuid = UUID.randomUUID();
        when(embeddingClientProvider.getIfAvailable()).thenReturn(null);
        when(embeddingRepositoryProvider.getIfAvailable()).thenReturn(null);

        service.refresh(userUuid, EmbeddingType.JOB);

        verifyNoInteractions(userRepository, embeddingClient, embeddingRepository);
    }

    @Test
    void generatesAndStoresChangedCloneSummaryEmbedding() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        Clone clone = mock(Clone.class);
        float[] embedding = new float[1536];
        when(embeddingClientProvider.getIfAvailable()).thenReturn(embeddingClient);
        when(embeddingRepositoryProvider.getIfAvailable()).thenReturn(embeddingRepository);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getUuid()).thenReturn(userUuid);
        when(cloneRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(clone));
        when(textBuilder.buildCloneSummaryText(clone))
                .thenReturn(Optional.of("클론 요약:\n새로운 경험을 즐기며 상대의 이야기를 잘 듣습니다."));
        when(embeddingRepository.findSourceHash(userUuid, EmbeddingType.CLONE_SUMMARY))
                .thenReturn(Optional.empty());
        when(embeddingClient.embed(any())).thenReturn(embedding);

        service.refresh(userUuid, EmbeddingType.CLONE_SUMMARY);

        verify(embeddingClient).embed("클론 요약:\n새로운 경험을 즐기며 상대의 이야기를 잘 듣습니다.");
        verify(embeddingRepository).upsert(
                eq(userUuid), eq(EmbeddingType.CLONE_SUMMARY), eq(embedding), any());
    }
}
