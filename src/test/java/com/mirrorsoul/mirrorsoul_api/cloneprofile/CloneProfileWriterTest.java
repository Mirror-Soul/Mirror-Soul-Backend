package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileGenerationInput;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.event.UserEmbeddingRefreshRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingType;
import com.mirrorsoul.mirrorsoul_api.repository.ClonePersonalityTagRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CloneProfileWriterTest {

    private final CloneRepository cloneRepository = mock(CloneRepository.class);
    private final ClonePersonalityTagRepository tagRepository =
            mock(ClonePersonalityTagRepository.class);
    private final CloneProfileGenerationJobRepository jobRepository =
            mock(CloneProfileGenerationJobRepository.class);
    private final CloneProfileSourceLoader sourceLoader = mock(CloneProfileSourceLoader.class);
    private final CloneProfileSourceHasher sourceHasher = mock(CloneProfileSourceHasher.class);
    private final CloneProfileVersionRepository versionRepository =
            mock(CloneProfileVersionRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final CloneProfileWriter writer = new CloneProfileWriter(
            cloneRepository, tagRepository, jobRepository, sourceLoader, sourceHasher,
            versionRepository, eventPublisher);

    @Test
    void publishesCloneSummaryEmbeddingEventAfterUpdatingProfile() {
        UUID userUuid = UUID.randomUUID();
        String sourceHash = "a".repeat(64);
        Clone clone = mock(Clone.class);
        CloneProfileGenerationJob job = mock(CloneProfileGenerationJob.class);
        CloneProfileGenerationInput source = mock(CloneProfileGenerationInput.class);
        GeneratedCloneProfile generated = new GeneratedCloneProfile(
                "새로운 경험을 즐기며 상대의 이야기를 잘 듣습니다.",
                List.of("호기심", "따뜻한 공감", "경청하는 사람"));

        when(clone.getId()).thenReturn(10L);
        when(job.getStatus()).thenReturn(CloneProfileGenerationJobStatus.PROCESSING);
        when(job.getPromptVersion()).thenReturn("clone-profile-v1");
        when(job.getSourceHash()).thenReturn(sourceHash);
        when(cloneRepository.findByUserUuidForUpdate(userUuid)).thenReturn(Optional.of(clone));
        when(jobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        when(sourceLoader.load(userUuid, "clone-profile-v1")).thenReturn(source);
        when(sourceHasher.hash(source)).thenReturn(sourceHash);

        assertTrue(writer.updateIfCurrent(20L, userUuid, sourceHash, generated));

        verify(clone).updateProfile(generated.summary(), sourceHash);
        verify(tagRepository).deleteAllByCloneId(10L);
        verify(tagRepository).flush();
        verify(tagRepository, org.mockito.Mockito.times(3)).save(any());
        ArgumentCaptor<CloneProfileVersion> versionCaptor =
                ArgumentCaptor.forClass(CloneProfileVersion.class);
        verify(versionRepository).save(versionCaptor.capture());
        CloneProfileVersion version = versionCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(generated.summary(), version.getSummary());
        org.junit.jupiter.api.Assertions.assertEquals(generated.personalityTags(), version.getPersonalityTags());
        org.junit.jupiter.api.Assertions.assertEquals(sourceHash, version.getSourceHash());
        org.junit.jupiter.api.Assertions.assertEquals("clone-profile-v1", version.getPromptVersion());
        org.junit.jupiter.api.Assertions.assertEquals(1, version.getVersionNumber());
        verify(job).complete(any());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserEmbeddingRefreshRequestedEvent event =
                (UserEmbeddingRefreshRequestedEvent) eventCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(userUuid, event.userUuid());
        org.junit.jupiter.api.Assertions.assertEquals(EmbeddingType.CLONE_SUMMARY, event.type());
    }

    @Test
    void staleGenerationDoesNotCreateProfileVersion() {
        UUID userUuid = UUID.randomUUID();
        Clone clone = mock(Clone.class);
        CloneProfileGenerationJob job = mock(CloneProfileGenerationJob.class);
        CloneProfileGenerationInput source = mock(CloneProfileGenerationInput.class);
        when(job.getStatus()).thenReturn(CloneProfileGenerationJobStatus.PROCESSING);
        when(job.getPromptVersion()).thenReturn("clone-profile-v1");
        when(cloneRepository.findByUserUuidForUpdate(userUuid)).thenReturn(Optional.of(clone));
        when(jobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        when(sourceLoader.load(userUuid, "clone-profile-v1")).thenReturn(source);
        when(sourceHasher.hash(source)).thenReturn("new-hash");

        org.junit.jupiter.api.Assertions.assertFalse(writer.updateIfCurrent(
                20L, userUuid, "old-hash", new GeneratedCloneProfile("summary", List.of("tag"))));

        verify(versionRepository, never()).save(any());
        verify(clone, never()).updateProfile(any(), any());
    }
}
