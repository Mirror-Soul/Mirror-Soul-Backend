package com.mirrorsoul.mirrorsoul_api.dto.valuebalance;

import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnswer;
import java.util.List;
import java.util.UUID;

public record ValueBalanceAnalysisMessageDTO(
        Long jobId,
        UUID userUuid,
        int setNumber,
        String previousSummary,
        List<Answer> answers
) {
    public record Answer(Long questionId, String axis, String leftLabel, String rightLabel,
                         String chosenSide, String chosenLabel) {
        public static Answer from(ValueBalanceAnswer answer) {
            var question = answer.getQuestion();
            String chosenLabel = answer.getChosenSide().name().equals("LEFT")
                    ? question.getLeftLabel() : question.getRightLabel();
            return new Answer(question.getId(), question.getAxis().name(), question.getLeftLabel(),
                    question.getRightLabel(), answer.getChosenSide().name(), chosenLabel);
        }
    }
}
