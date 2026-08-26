package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.event.ValueBalanceAnalysisRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ValueBalanceServiceTest {
    private UserRepository userRepository;
    private ValueBalanceQuestionRepository questionRepository;
    private ValueBalanceAnswerRepository answerRepository;
    private UserValueAxisScoreRepository scoreRepository;
    private ValueBalanceAnalysisJobRepository jobRepository;
    private ApplicationEventPublisher eventPublisher;
    private ValueBalanceService service;
    private UUID userUuid;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        questionRepository = mock(ValueBalanceQuestionRepository.class);
        answerRepository = mock(ValueBalanceAnswerRepository.class);
        scoreRepository = mock(UserValueAxisScoreRepository.class);
        jobRepository = mock(ValueBalanceAnalysisJobRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ValueBalanceService(userRepository, questionRepository, answerRepository,
                scoreRepository, jobRepository, eventPublisher);
        userUuid = UUID.randomUUID();
        user = User.builder().id(1L).uuid(userUuid).email("user@example.com").passwordHash("password").build();
    }

    @Test
    void returnsRandomNeverAnsweredQuestionWithSetProgress() {
        ValueBalanceQuestion question = question(10L, ValueBalanceAxis.LOVE);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserId(1L)).thenReturn(3L);
        when(questionRepository.findActiveNeverAnswered(1L)).thenReturn(List.of(question));

        var result = service.getQuestion(userUuid);

        assertThat(result.questionId()).isEqualTo(10L);
        assertThat(result.currentSet()).isEqualTo(1);
        assertThat(result.answeredInSet()).isEqualTo(3);
        assertThat(result.setSize()).isEqualTo(8);
        assertThat(result.totalSets()).isEqualTo(13);
        assertThat(result.locked()).isFalse();
    }

    @Test
    void returnsLockedStateForTwelveHoursAfterSetCompletion() {
        ValueBalanceAnalysisJob job = mock(ValueBalanceAnalysisJob.class);
        when(job.getCreatedAt()).thenReturn(LocalDateTime.now().minusHours(1));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserId(1L)).thenReturn(8L);
        when(jobRepository.findFirstByUserIdOrderBySetNumberDesc(1L)).thenReturn(Optional.of(job));

        var result = service.getQuestion(userUuid);

        assertThat(result.questionId()).isNull();
        assertThat(result.locked()).isTrue();
        assertThat(result.currentSet()).isEqualTo(2);
        assertThat(result.lockedUntil()).isAfter(LocalDateTime.now());
        verifyNoInteractions(questionRepository);
    }

    @Test
    void eighthAnswerCreatesOneAnalysisJobAndLocksNextSet() {
        ValueBalanceQuestion question = question(10L, ValueBalanceAxis.LOVE);
        UserValueAxisScore score = UserValueAxisScore.initialize(user, ValueBalanceAxis.LOVE);
        when(userRepository.findByUuidForUpdate(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserId(1L)).thenReturn(7L);
        when(questionRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(question));
        when(answerRepository.existsByUserIdAndQuestionId(1L, 10L)).thenReturn(false);
        when(scoreRepository.findByUserIdAndAxisForUpdate(1L, ValueBalanceAxis.LOVE)).thenReturn(Optional.of(score));
        when(jobRepository.save(any())).thenAnswer(invocation -> {
            ValueBalanceAnalysisJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 99L);
            return job;
        });

        var result = service.submitAnswer(userUuid, 10L,
                new EvolveReqDTO.ValueBalanceAnswerDTO(ValueBalanceChosenSide.RIGHT));

        assertThat(result.totalAnswered()).isEqualTo(8);
        assertThat(result.locked()).isTrue();
        assertThat(result.analysisJobId()).isEqualTo(99L);
        assertThat(result.currentSet()).isEqualTo(2);
        verify(eventPublisher).publishEvent(new ValueBalanceAnalysisRequestedEvent(99L));
    }

    @Test
    void rejectsAnAnsweredQuestionPermanently() {
        ValueBalanceQuestion question = question(10L, ValueBalanceAxis.LOVE);
        when(userRepository.findByUuidForUpdate(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserId(1L)).thenReturn(2L);
        when(questionRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(question));
        when(answerRepository.existsByUserIdAndQuestionId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.submitAnswer(userUuid, 10L,
                new EvolveReqDTO.ValueBalanceAnswerDTO(ValueBalanceChosenSide.LEFT)))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getCode())
                        .isEqualTo(GeneralErrorCode.VALUE_BALANCE_ALREADY_ANSWERED));
    }

    @Test
    void marksGameCompletedAfterTheLastAnswerWithoutAnotherLock() {
        ValueBalanceQuestion question = question(104L, ValueBalanceAxis.TASTE);
        UserValueAxisScore score = UserValueAxisScore.initialize(user, ValueBalanceAxis.TASTE);
        when(userRepository.findByUuidForUpdate(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserId(1L)).thenReturn(103L);
        when(questionRepository.findByIdAndActiveTrue(104L)).thenReturn(Optional.of(question));
        when(scoreRepository.findByUserIdAndAxisForUpdate(1L, ValueBalanceAxis.TASTE)).thenReturn(Optional.of(score));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.submitAnswer(userUuid, 104L,
                new EvolveReqDTO.ValueBalanceAnswerDTO(ValueBalanceChosenSide.LEFT));

        assertThat(result.completed()).isTrue();
        assertThat(result.locked()).isFalse();
        assertThat(result.currentSet()).isEqualTo(13);
    }

    private ValueBalanceQuestion question(Long id, ValueBalanceAxis axis) {
        ValueBalanceQuestion question = mock(ValueBalanceQuestion.class);
        when(question.getId()).thenReturn(id);
        when(question.getAxis()).thenReturn(axis);
        when(question.getLeftLabel()).thenReturn("left");
        when(question.getRightLabel()).thenReturn("right");
        return question;
    }
}
