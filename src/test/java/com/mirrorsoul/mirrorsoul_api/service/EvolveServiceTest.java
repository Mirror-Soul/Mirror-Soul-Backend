package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingSentence;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
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
    private EvolveService service;

    @BeforeEach
    void setUp() {
        cloneRepository = mock(CloneRepository.class);
        voiceTrainingJobRepository = mock(VoiceTrainingJobRepository.class);
        voiceTrainingSentenceRepository = mock(VoiceTrainingSentenceRepository.class);
        service = new EvolveService(
                cloneRepository,
                mock(UserRepository.class),
                voiceTrainingSentenceRepository,
                voiceTrainingJobRepository,
                mock(FileService.class),
                mock(VoiceTrainingJobService.class),
                mock(ApplicationEventPublisher.class)
        );
    }

    @Test
    void twinSyncIncludesVoiceTrainingCountAndLatestSubmissionTime() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime latestSubmission = LocalDateTime.of(2026, 8, 13, 14, 30);
        VoiceTrainingJob latestJob = mock(VoiceTrainingJob.class);

        when(cloneRepository.findSyncRateByUserUuid(userUuid)).thenReturn(Optional.of(76));
        when(voiceTrainingJobRepository.countByUser_UuidAndSource(
                userUuid, VoiceTrainingJobSource.VOICE_UPDATE)).thenReturn(3L);
        when(voiceTrainingJobRepository.findFirstByUser_UuidAndSourceOrderByCreatedAtDescIdDesc(
                userUuid, VoiceTrainingJobSource.VOICE_UPDATE)).thenReturn(Optional.of(latestJob));
        when(latestJob.getCreatedAt()).thenReturn(latestSubmission);

        EvolveResDTO.twinSyncDTO result = service.twinSync(userUuid);

        assertThat(result.getSyncRate()).isEqualTo(76);
        assertThat(result.getVoiceTrainingCount()).isEqualTo(3L);
        assertThat(result.getLastVoiceTrainingAt()).isEqualTo(latestSubmission);
    }

    @Test
    void twinSyncReturnsZeroAndNullWhenVoiceHasNeverBeenTrained() {
        UUID userUuid = UUID.randomUUID();
        when(cloneRepository.findSyncRateByUserUuid(userUuid)).thenReturn(Optional.of(76));
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
}
