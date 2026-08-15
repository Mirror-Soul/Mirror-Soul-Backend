package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceTrainingJobServiceTest {

    private VoiceTrainingJobRepository voiceTrainingJobRepository;
    private VoiceTrainingJobService service;
    private User user;

    @BeforeEach
    void setUp() {
        voiceTrainingJobRepository = mock(VoiceTrainingJobRepository.class);
        service = new VoiceTrainingJobService(
                voiceTrainingJobRepository,
                mock(InterviewRecordRepository.class),
                mock(AwsS3Properties.class)
        );
        user = User.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("password")
                .build();
    }

    @Test
    void rejectsVoiceUpdateSubmittedWithinTwoMinutes() {
        when(voiceTrainingJobRepository.existsByUser_IdAndSourceAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(VoiceTrainingJobSource.VOICE_UPDATE),
                any(LocalDateTime.class)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.assertVoiceUpdateSubmissionAllowed(user))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.VOICE_TRAINING_TOO_FREQUENT));
    }
}
