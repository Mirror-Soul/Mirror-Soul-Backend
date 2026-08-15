package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserValueAxisScore;
import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnswer;
import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceQuestion;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserValueAxisScoreRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceAnswerRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceQuestionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValueBalanceService {

    static final int DAILY_LIMIT = 5;
    static final int REEXPOSURE_DAYS = 14;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final ValueBalanceQuestionRepository questionRepository;
    private final ValueBalanceAnswerRepository answerRepository;
    private final UserValueAxisScoreRepository scoreRepository;

    public EvolveResDTO.valueBalanceQuestionDTO getQuestion(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        TimeWindow time = currentTimeWindow();

        long answeredCount = answerRepository
                .countByUserIdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                        user.getId(), time.todayStart(), time.tomorrowStart());
        if (answeredCount >= DAILY_LIMIT) {
            return new EvolveResDTO.valueBalanceQuestionDTO(
                    null, null, null, null, Math.toIntExact(answeredCount), DAILY_LIMIT);
        }

        List<ValueBalanceQuestion> candidates = questionRepository.findActiveNotAnsweredSince(
                user.getId(), time.now().minusDays(REEXPOSURE_DAYS));
        if (candidates.isEmpty()) {
            candidates = questionRepository.findActiveNotAnsweredSince(user.getId(), time.todayStart());
        }
        if (candidates.isEmpty()) {
            throw new GeneralException(GeneralErrorCode.VALUE_BALANCE_NO_AVAILABLE_QUESTION);
        }

        Set<ValueBalanceAxis> answeredAxes = answerRepository.findAnsweredAxes(
                user.getId(), time.todayStart(), time.tomorrowStart());
        List<ValueBalanceQuestion> diverseCandidates = candidates.stream()
                .filter(question -> !answeredAxes.contains(question.getAxis()))
                .toList();
        List<ValueBalanceQuestion> selectionPool = diverseCandidates.isEmpty()
                ? candidates : diverseCandidates;
        ValueBalanceQuestion selected = selectionPool.get(
                ThreadLocalRandom.current().nextInt(selectionPool.size()));

        return new EvolveResDTO.valueBalanceQuestionDTO(
                selected.getId(),
                selected.getAxis(),
                selected.getLeftLabel(),
                selected.getRightLabel(),
                Math.toIntExact(answeredCount),
                DAILY_LIMIT
        );
    }

    @Transactional
    public EvolveResDTO.valueBalanceAnswerDTO submitAnswer(
            UUID userUuid,
            Long questionId,
            EvolveReqDTO.ValueBalanceAnswerDTO request
    ) {
        User user = userRepository.findByUuidForUpdate(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        TimeWindow time = currentTimeWindow();

        long answeredCount = answerRepository
                .countByUserIdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                        user.getId(), time.todayStart(), time.tomorrowStart());
        if (answeredCount >= DAILY_LIMIT) {
            throw new GeneralException(GeneralErrorCode.VALUE_BALANCE_DAILY_LIMIT_REACHED);
        }

        ValueBalanceQuestion question = questionRepository.findByIdAndActiveTrue(questionId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.VALUE_BALANCE_QUESTION_NOT_FOUND));

        if (answerRepository.existsByUserIdAndQuestionIdAndAnsweredAtGreaterThanEqual(
                user.getId(), questionId, time.todayStart())) {
            throw new GeneralException(GeneralErrorCode.VALUE_BALANCE_ALREADY_ANSWERED);
        }

        answerRepository.save(ValueBalanceAnswer.builder()
                .user(user)
                .question(question)
                .chosenSide(request.chosenSide())
                .build());

        UserValueAxisScore axisScore = scoreRepository
                .findByUserIdAndAxisForUpdate(user.getId(), question.getAxis())
                .orElseGet(() -> UserValueAxisScore.initialize(user, question.getAxis()));
        axisScore.addSample(request.chosenSide().getScoreValue());
        scoreRepository.save(axisScore);

        return new EvolveResDTO.valueBalanceAnswerDTO(
                questionId,
                Math.toIntExact(answeredCount + 1),
                DAILY_LIMIT
        );
    }

    private TimeWindow currentTimeWindow() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        LocalDate today = now.toLocalDate();
        return new TimeWindow(now, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private record TimeWindow(
            LocalDateTime now,
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart
    ) {
    }
}
