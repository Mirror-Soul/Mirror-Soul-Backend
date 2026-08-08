package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserValueAxisScore;
import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnswer;
import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceQuestion;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceChosenSide;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserValueAxisScoreRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceAnswerRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceQuestionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ValueBalanceServiceTest {

    private UserRepository userRepository;
    private ValueBalanceQuestionRepository questionRepository;
    private ValueBalanceAnswerRepository answerRepository;
    private UserValueAxisScoreRepository scoreRepository;
    private ValueBalanceService service;
    private UUID userUuid;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        questionRepository = mock(ValueBalanceQuestionRepository.class);
        answerRepository = mock(ValueBalanceAnswerRepository.class);
        scoreRepository = mock(UserValueAxisScoreRepository.class);
        service = new ValueBalanceService(
                userRepository, questionRepository, answerRepository, scoreRepository);
        userUuid = UUID.randomUUID();
        user = User.builder()
                .id(1L)
                .uuid(userUuid)
                .email("user@example.com")
                .passwordHash("password")
                .build();
    }

    @Test
    void returnsEmptyAfterFiveAnswersToday() {
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserIdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(5L);

        assertThat(service.getQuestion(userUuid)).isEmpty();
    }

    @Test
    void prefersAnAxisNotAnsweredToday() {
        ValueBalanceQuestion love = question(10L, ValueBalanceAxis.LOVE);
        ValueBalanceQuestion comm = question(11L, ValueBalanceAxis.COMM);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserIdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(questionRepository.findActiveNotAnsweredSince(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of(love, comm));
        when(answerRepository.findAnsweredAxes(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Set.of(ValueBalanceAxis.LOVE));

        EvolveResDTO.valueBalanceQuestionDTO result = service.getQuestion(userUuid).orElseThrow();

        assertThat(result.questionId()).isEqualTo(11L);
        assertThat(result.axis()).isEqualTo(ValueBalanceAxis.COMM);
    }

    @Test
    void savesAnswerAndUpdatesAxisScore() {
        ValueBalanceQuestion question = question(10L, ValueBalanceAxis.LOVE);
        UserValueAxisScore score = UserValueAxisScore.initialize(user, ValueBalanceAxis.LOVE);
        when(userRepository.findByUuidForUpdate(userUuid)).thenReturn(Optional.of(user));
        when(answerRepository.countByUserIdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(2L);
        when(questionRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(question));
        when(answerRepository.existsByUserIdAndQuestionIdAndAnsweredAtGreaterThanEqual(
                anyLong(), anyLong(), any(LocalDateTime.class))).thenReturn(false);
        when(scoreRepository.findByUserIdAndAxisForUpdate(1L, ValueBalanceAxis.LOVE))
                .thenReturn(Optional.of(score));

        EvolveResDTO.valueBalanceAnswerDTO result = service.submitAnswer(
                userUuid,
                10L,
                new EvolveReqDTO.ValueBalanceAnswerDTO(ValueBalanceChosenSide.RIGHT));

        assertThat(result.answeredCount()).isEqualTo(3);
        assertThat(result.dailyLimit()).isEqualTo(5);
        assertThat(score.getScore()).isEqualByComparingTo("1.0000");
        assertThat(score.getSampleCount()).isEqualTo(1);
        ArgumentCaptor<ValueBalanceAnswer> answerCaptor = ArgumentCaptor.forClass(ValueBalanceAnswer.class);
        verify(answerRepository).save(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getChosenSide()).isEqualTo(ValueBalanceChosenSide.RIGHT);
        verify(scoreRepository).save(score);
    }

    @Test
    void rejectsQuestionAlreadyAnsweredToday() {
        ValueBalanceQuestion question = question(10L, ValueBalanceAxis.LOVE);
        when(userRepository.findByUuidForUpdate(userUuid)).thenReturn(Optional.of(user));
        when(questionRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(question));
        when(answerRepository.existsByUserIdAndQuestionIdAndAnsweredAtGreaterThanEqual(
                anyLong(), anyLong(), any(LocalDateTime.class))).thenReturn(true);

        assertThatThrownBy(() -> service.submitAnswer(
                userUuid,
                10L,
                new EvolveReqDTO.ValueBalanceAnswerDTO(ValueBalanceChosenSide.LEFT)))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.VALUE_BALANCE_ALREADY_ANSWERED));
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
