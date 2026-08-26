package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.evolve.EvolveResDTO;
import com.mirrorsoul.mirrorsoul_api.event.ValueBalanceAnalysisRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValueBalanceService {
    static final int SET_SIZE = 8;
    static final int TOTAL_SETS = 13;
    static final int TOTAL_QUESTIONS = SET_SIZE * TOTAL_SETS;
    static final int LOCK_HOURS = 12;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final ValueBalanceQuestionRepository questionRepository;
    private final ValueBalanceAnswerRepository answerRepository;
    private final UserValueAxisScoreRepository scoreRepository;
    private final ValueBalanceAnalysisJobRepository analysisJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EvolveResDTO.valueBalanceQuestionDTO getQuestion(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        long totalAnswers = answerRepository.countByUserId(user.getId());
        Progress progress = progress(user.getId(), totalAnswers, now());
        if (progress.completed() || progress.locked()) return questionResponse(null, progress);
        List<ValueBalanceQuestion> candidates = questionRepository.findActiveNeverAnswered(user.getId());
        if (candidates.isEmpty()) throw new GeneralException(GeneralErrorCode.VALUE_BALANCE_NO_AVAILABLE_QUESTION);
        ValueBalanceQuestion selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        return questionResponse(selected, progress);
    }

    @Transactional
    public EvolveResDTO.valueBalanceAnswerDTO submitAnswer(
            UUID userUuid, Long questionId, EvolveReqDTO.ValueBalanceAnswerDTO request) {
        User user = userRepository.findByUuidForUpdate(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        long totalAnswers = answerRepository.countByUserId(user.getId());
        Progress before = progress(user.getId(), totalAnswers, now());
        if (before.completed()) throw new GeneralException(GeneralErrorCode.VALUE_BALANCE_COMPLETED);
        if (before.locked()) throw new GeneralException(GeneralErrorCode.VALUE_BALANCE_SET_LOCKED);

        ValueBalanceQuestion question = questionRepository.findByIdAndActiveTrue(questionId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.VALUE_BALANCE_QUESTION_NOT_FOUND));
        if (answerRepository.existsByUserIdAndQuestionId(user.getId(), questionId))
            throw new GeneralException(GeneralErrorCode.VALUE_BALANCE_ALREADY_ANSWERED);

        answerRepository.save(ValueBalanceAnswer.builder().user(user).question(question)
                .chosenSide(request.chosenSide()).build());
        UserValueAxisScore axisScore = scoreRepository.findByUserIdAndAxisForUpdate(user.getId(), question.getAxis())
                .orElseGet(() -> UserValueAxisScore.initialize(user, question.getAxis()));
        axisScore.addSample(request.chosenSide().getScoreValue());
        scoreRepository.save(axisScore);

        long updatedTotal = totalAnswers + 1;
        Long analysisJobId = null;
        LocalDateTime lockedUntil = null;
        if (updatedTotal % SET_SIZE == 0) {
            int setNumber = Math.toIntExact(updatedTotal / SET_SIZE);
            ValueBalanceAnalysisJob job = analysisJobRepository.save(ValueBalanceAnalysisJob.create(user, setNumber));
            analysisJobId = job.getId();
            eventPublisher.publishEvent(new ValueBalanceAnalysisRequestedEvent(job.getId()));
            if (updatedTotal < TOTAL_QUESTIONS) lockedUntil = now().plusHours(LOCK_HOURS);
        }
        return answerResponse(questionId, updatedTotal, analysisJobId, lockedUntil);
    }

    private Progress progress(Long userId, long totalAnswers, LocalDateTime currentTime) {
        boolean completed = totalAnswers >= TOTAL_QUESTIONS;
        LocalDateTime lockedUntil = null;
        if (!completed && totalAnswers > 0 && totalAnswers % SET_SIZE == 0) {
            lockedUntil = analysisJobRepository.findFirstByUserIdOrderBySetNumberDesc(userId)
                    .map(ValueBalanceAnalysisJob::getCreatedAt)
                    .map(createdAt -> createdAt.plusHours(LOCK_HOURS)).orElse(null);
        }
        boolean locked = lockedUntil != null && currentTime.isBefore(lockedUntil);
        int currentSet = completed ? TOTAL_SETS : Math.toIntExact(totalAnswers / SET_SIZE) + 1;
        int answeredInSet = completed ? SET_SIZE : Math.toIntExact(totalAnswers % SET_SIZE);
        return new Progress(currentSet, answeredInSet, Math.toIntExact(totalAnswers),
                locked, locked ? lockedUntil : null, completed);
    }

    private EvolveResDTO.valueBalanceQuestionDTO questionResponse(ValueBalanceQuestion q, Progress p) {
        return new EvolveResDTO.valueBalanceQuestionDTO(q == null ? null : q.getId(), q == null ? null : q.getAxis(),
                q == null ? null : q.getLeftLabel(), q == null ? null : q.getRightLabel(), p.currentSet(),
                p.answeredInSet(), SET_SIZE, TOTAL_SETS, p.totalAnswers(), p.locked(), p.lockedUntil(), p.completed());
    }

    private EvolveResDTO.valueBalanceAnswerDTO answerResponse(
            Long questionId, long total, Long jobId, LocalDateTime lockedUntil) {
        boolean completed = total >= TOTAL_QUESTIONS;
        int currentSet = completed ? TOTAL_SETS : Math.toIntExact(total / SET_SIZE) + 1;
        int answeredInSet = completed ? SET_SIZE : Math.toIntExact(total % SET_SIZE);
        return new EvolveResDTO.valueBalanceAnswerDTO(questionId, currentSet, answeredInSet, SET_SIZE,
                TOTAL_SETS, Math.toIntExact(total), lockedUntil != null, lockedUntil, completed, jobId);
    }

    private LocalDateTime now() { return LocalDateTime.now(SERVICE_ZONE); }

    private record Progress(int currentSet, int answeredInSet, int totalAnswers,
                            boolean locked, LocalDateTime lockedUntil, boolean completed) {}
}
