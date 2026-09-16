package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.visual.FaceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.dto.visual.FaceTrainingResultDTO.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class FaceTrainingResultServiceTest {
    private final FaceTrainingJobRepository jobs = mock(FaceTrainingJobRepository.class);
    private final CloneRepository clones = mock(CloneRepository.class);
    private final AiFaceProfileRepository profiles = mock(AiFaceProfileRepository.class);
    private final CloneReadinessService readiness = mock(CloneReadinessService.class);
    private final AwsS3Properties storage = new AwsS3Properties();
    private FaceTrainingResultService service;
    private FaceTrainingJob job;
    private Clone clone;
    private UUID uuid;

    @BeforeEach
    void setup() {
        uuid = UUID.randomUUID();
        User user = User.builder().id(1L).uuid(uuid).build();
        clone = Clone.builder().id(2L).user(user).build();
        job = FaceTrainingJob.create(user, FaceTrainingJobSource.ONBOARDING_FACE);
        ReflectionTestUtils.setField(job, "id", 3L);
        when(clones.findLockedById(2L)).thenReturn(Optional.of(clone));
        when(jobs.findLockedById(3L)).thenReturn(Optional.of(job));
        when(profiles.findAllByCloneIdAndActiveTrue(2L)).thenReturn(List.of());
        storage.setBucket("test-bucket");
        service = new FaceTrainingResultService(jobs, clones, profiles, storage, readiness);
    }

    private FaceTrainingResultDTO message(String status, Result result, Failure error) {
        return new FaceTrainingResultDTO("FACE_PROFILE_BUILD_STATUS", 3L, uuid, 2L, status, result, error);
    }

    private Result result(boolean passed) {
        String prefix = "face-results/" + uuid + "/job-3/";
        return new Result("READY_FOR_RENDERING", new Artifacts("test-bucket", prefix + "face-profile.json",
                prefix + "portrait.jpg", prefix + "preprocess-manifest.json", prefix + "preview.mp4"), passed);
    }

    @Test
    void completionPersistsArtifactsAndIgnoresDuplicateAndLateProcessing() {
        var completed = message("COMPLETED", result(true), null);
        service.handle(completed);
        service.handle(completed);
        service.handle(message("PROCESSING", null, null));
        service.handle(message("FAILED", null, new Failure("LATE", "late", false)));
        var saved = ArgumentCaptor.forClass(AiFaceProfile.class);
        verify(profiles).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getProfileKey()).endsWith("/face-profile.json");
        assertThat(saved.getValue().getManifestKey()).endsWith("/preprocess-manifest.json");
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getStatus()).isEqualTo("READY");
        assertThat(job.getStatus()).isEqualTo(FaceTrainingJobStatus.COMPLETED);
        assertThat(job.getFinishedAt()).isNotNull();
        verify(readiness).refreshLocked(clone);
    }

    @Test
    void retryableFailureRemainsProcessingAndAllowsCompletion() {
        service.handle(message("FAILED", null, new Failure("GPU_BUSY", "Try again", true)));
        assertThat(job.getStatus()).isEqualTo(FaceTrainingJobStatus.PROCESSING);
        assertThat(job.getErrorRetryable()).isTrue();
        assertThat(job.getFinishedAt()).isNull();
        service.handle(message("COMPLETED", result(true), null));
        assertThat(job.getStatus()).isEqualTo(FaceTrainingJobStatus.COMPLETED);
        assertThat(job.getErrorMessage()).isNull();
    }

    @Test
    void permanentFailureIsTerminal() {
        service.handle(message("FAILED", null, new Failure("NO_FACE", "No face", false)));
        service.handle(message("COMPLETED", result(true), null));
        assertThat(job.getStatus()).isEqualTo(FaceTrainingJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("NO_FACE");
        verifyNoInteractions(profiles);
    }

    @Test
    void failedQualityGateDoesNotActivateProfile() {
        service.handle(message("COMPLETED", result(false), null));
        assertThat(job.getStatus()).isEqualTo(FaceTrainingJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("QUALITY_GATE_FAILED");
        verifyNoInteractions(profiles, readiness);
    }

    @Test
    void rejectsWrongOwnerAndForeignArtifactPaths() {
        assertThatThrownBy(() -> service.handle(new FaceTrainingResultDTO("FACE_PROFILE_BUILD_STATUS",
                3L, UUID.randomUUID(), 2L, "PROCESSING", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        var bad = new Result("READY_FOR_RENDERING",
                new Artifacts("test-bucket", "other-user/face-profile.json", "portrait.jpg", "manifest.json", null), true);
        assertThatThrownBy(() -> service.handle(message("COMPLETED", bad, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(job.getStatus()).isEqualTo(FaceTrainingJobStatus.PENDING);
        verifyNoInteractions(profiles);
    }

    @Test
    void missingRetryabilityDoesNotFinalizeJob() {
        assertThatThrownBy(() -> service.handle(message("FAILED", null, new Failure("ERR", "error", null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(job.getStatus()).isEqualTo(FaceTrainingJobStatus.PENDING);
    }

    @Test
    void olderResultCannotReplaceNewerActiveProfile() {
        FaceTrainingJob newerJob = FaceTrainingJob.create(job.getUser(), FaceTrainingJobSource.ONBOARDING_FACE);
        ReflectionTestUtils.setField(newerJob, "id", 4L);
        AiFaceProfile newer = AiFaceProfile.ready(clone, newerJob, "test-bucket", "profile", "portrait", "manifest", null);
        when(profiles.findAllByCloneIdAndActiveTrue(2L)).thenReturn(List.of(newer));
        service.handle(message("COMPLETED", result(true), null));
        var saved = ArgumentCaptor.forClass(AiFaceProfile.class);
        verify(profiles).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(newer.isActive()).isTrue();
    }

    @Test
    void newerResultDeactivatesOlderProfile() {
        FaceTrainingJob olderJob = FaceTrainingJob.create(job.getUser(), FaceTrainingJobSource.ONBOARDING_FACE);
        ReflectionTestUtils.setField(olderJob, "id", 1L);
        AiFaceProfile older = AiFaceProfile.ready(clone, olderJob, "test-bucket", "profile", "portrait", "manifest", null);
        when(profiles.findAllByCloneIdAndActiveTrue(2L)).thenReturn(List.of(older));
        service.handle(message("COMPLETED", result(true), null));
        assertThat(older.isActive()).isFalse();
    }
}
