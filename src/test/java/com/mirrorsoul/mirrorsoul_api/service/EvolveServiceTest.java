package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.FaceFile;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingSentence;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.event.FaceTrainingJobRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.FaceFileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingSentenceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class EvolveServiceTest {

    private CloneRepository cloneRepository;
    private VoiceTrainingJobRepository voiceTrainingJobRepository;
    private VoiceTrainingSentenceRepository voiceTrainingSentenceRepository;
    private UserRepository userRepository;
    private FaceFileRepository faceFileRepository;
    private FileService fileService;
    private FaceTrainingJobService faceTrainingJobService;
    private ApplicationEventPublisher eventPublisher;
    private EvolveService service;

    @BeforeEach
    void setUp() {
        cloneRepository = mock(CloneRepository.class);
        voiceTrainingJobRepository = mock(VoiceTrainingJobRepository.class);
        voiceTrainingSentenceRepository = mock(VoiceTrainingSentenceRepository.class);
        userRepository = mock(UserRepository.class);
        faceFileRepository = mock(FaceFileRepository.class);
        fileService = mock(FileService.class);
        faceTrainingJobService = mock(FaceTrainingJobService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new EvolveService(
                cloneRepository,
                userRepository,
                voiceTrainingSentenceRepository,
                voiceTrainingJobRepository,
                fileService,
                mock(VoiceTrainingJobService.class),
                faceFileRepository,
                faceTrainingJobService,
                eventPublisher
        );
    }

    @Test
    void twinSyncIncludesVoiceTrainingCountAndLatestSubmissionTime() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime latestSubmission = LocalDateTime.of(2026, 8, 13, 14, 30);
        VoiceTrainingJob latestJob = mock(VoiceTrainingJob.class);

        var clone = com.mirrorsoul.mirrorsoul_api.domain.Clone.builder()
                .syncRate(new java.math.BigDecimal("76.6")).status("READY").build();
        when(cloneRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(clone));
        when(voiceTrainingJobRepository.countByUser_UuidAndSource(
                userUuid, VoiceTrainingJobSource.VOICE_UPDATE)).thenReturn(3L);
        when(voiceTrainingJobRepository.findFirstByUser_UuidAndSourceOrderByCreatedAtDescIdDesc(
                userUuid, VoiceTrainingJobSource.VOICE_UPDATE)).thenReturn(Optional.of(latestJob));
        when(latestJob.getCreatedAt()).thenReturn(latestSubmission);

        EvolveResDTO.twinSyncDTO result = service.twinSync(userUuid);

        assertThat(result.getSyncRate()).isEqualTo(new java.math.BigDecimal("76.6"));
        assertThat(result.getVoiceTrainingCount()).isEqualTo(3L);
        assertThat(result.getLastVoiceTrainingAt()).isEqualTo(latestSubmission);
    }

    @Test
    void twinSyncReturnsZeroAndNullWhenVoiceHasNeverBeenTrained() {
        UUID userUuid = UUID.randomUUID();
        var clone = com.mirrorsoul.mirrorsoul_api.domain.Clone.builder()
                .syncRate(java.math.BigDecimal.valueOf(76)).status("READY").build();
        when(cloneRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(clone));
        when(voiceTrainingJobRepository.findFirstByUser_UuidAndSourceOrderByCreatedAtDescIdDesc(
                userUuid, VoiceTrainingJobSource.VOICE_UPDATE)).thenReturn(Optional.empty());

        EvolveResDTO.twinSyncDTO result = service.twinSync(userUuid);

        assertThat(result.getVoiceTrainingCount()).isZero();
        assertThat(result.getLastVoiceTrainingAt()).isNull();
    }

    @Test
    void speechLineExcludesRecentlyUsedSentences() {
        UUID userUuid = UUID.randomUUID();
        VoiceTrainingSentence recentSentence = mock(VoiceTrainingSentence.class);
        VoiceTrainingSentence nextSentence = mock(VoiceTrainingSentence.class);
        VoiceTrainingJob recentJob = mock(VoiceTrainingJob.class);
        when(recentSentence.getId()).thenReturn(10L);
        when(nextSentence.getId()).thenReturn(20L);
        when(nextSentence.getContent()).thenReturn("새로운 문장");
        when(recentJob.getVoiceTrainingSentence()).thenReturn(recentSentence);
        when(voiceTrainingJobRepository
                .findTop5ByUser_UuidAndSourceAndVoiceTrainingSentenceIsNotNullOrderByCreatedAtDescIdDesc(
                        userUuid, VoiceTrainingJobSource.VOICE_UPDATE))
                .thenReturn(List.of(recentJob));

        when(voiceTrainingSentenceRepository.findRandomActiveExcluding(List.of(10L)))
                .thenReturn(Optional.of(nextSentence));

        EvolveResDTO.speechLineDTO result = service.speechLine(userUuid);

        assertThat(result.getSentenceId()).isEqualTo(20L);
        assertThat(result.getSpeechLine()).isEqualTo("새로운 문장");
    }

    @Test
    void twinSyncHidesPendingScoreWithoutTreatingExistingCloneAsMissing() {
        UUID userUuid = UUID.randomUUID();
        var clone = com.mirrorsoul.mirrorsoul_api.domain.Clone.builder()
                .syncRate(new java.math.BigDecimal("28.5")).status("PENDING").build();
        when(cloneRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(clone));
        assertThat(service.twinSync(userUuid).getSyncRate()).isNull();
    }

    @Test
    void faceUpdateCreatesJobAndRequestsQueuePublication() {
        UUID uuid = UUID.randomUUID();
        User user = User.builder().id(1L).uuid(uuid).status(UserStatus.ACTIVE).build();
        String objectKey = "face-images/" + uuid + "/new-face.jpg";
        when(userRepository.findByUuid(uuid)).thenReturn(Optional.of(user));
        when(cloneRepository.findByUserUuid(uuid))
                .thenReturn(Optional.of(mock(com.mirrorsoul.mirrorsoul_api.domain.Clone.class)));
        when(fileService.verifyFaceUpdateMediaAndBuildFileUrl(uuid, objectKey))
                .thenReturn(new FileService.VerifiedS3Object("https://example.com/" + objectKey, objectKey));
        when(faceFileRepository.save(any(FaceFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FaceTrainingJob job = mock(FaceTrainingJob.class);
        when(job.getId()).thenReturn(42L);
        when(job.getStatus()).thenReturn(FaceTrainingJobStatus.PENDING);
        when(faceTrainingJobService.createPendingJob(eq(user), any(FaceFile.class),
                eq(FaceTrainingJobSource.FACE_UPDATE))).thenReturn(job);

        EvolveResDTO.faceUpdateJobDTO result = service.completeFaceUpdate(
                uuid, new EvolveReqDTO.FaceUpdateCompleteDTO(objectKey));

        assertThat(result.getJobId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(faceTrainingJobService).createPendingJob(eq(user),
                org.mockito.ArgumentMatchers.argThat(file -> objectKey.equals(file.getObjectKey())),
                eq(FaceTrainingJobSource.FACE_UPDATE));
        verify(eventPublisher).publishEvent(new FaceTrainingJobRequestedEvent(42L));
    }

    @Test
    void faceUpdateRejectsUsersWhoHaveNotCompletedOnboarding() {
        UUID uuid = UUID.randomUUID();
        User user = User.builder().id(1L).uuid(uuid).status(UserStatus.ONBOARD_D).build();
        when(userRepository.findByUuid(uuid)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeFaceUpdate(
                uuid, new EvolveReqDTO.FaceUpdateCompleteDTO("face-images/" + uuid + "/face.jpg")))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.FORBIDDEN));
        verifyNoInteractions(fileService, faceFileRepository, faceTrainingJobService, eventPublisher);
    }
}
