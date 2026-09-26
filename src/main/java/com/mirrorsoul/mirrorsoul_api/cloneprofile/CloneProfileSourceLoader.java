package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileGenerationInput;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.InterviewAnswer;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.MbtiAxisScores;
import com.mirrorsoul.mirrorsoul_api.config.CloneProfileGenerationProperties;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAnalysisJobStatus;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ValueBalanceAnalysisJobRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CloneProfileSourceLoader {

    private final UserRepository userRepository;
    private final MbtiProfileRepository mbtiProfileRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final ValueBalanceAnalysisJobRepository analysisJobRepository;
    private final CloneProfileGenerationProperties properties;

    @Transactional(readOnly = true)
    public CloneProfileGenerationInput load(UUID userUuid, String promptVersion) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new CloneProfileSourceException("Clone profile source user was not found"));
        MbtiProfile mbti = mbtiProfileRepository.findByUser_Id(user.getId()).orElse(null);

        int remaining = properties.getMaxTotalCharacters();
        String selfIntroduction = fit(normalize(user.getSelfIntroduction()), remaining);
        remaining -= selfIntroduction.length();

        List<InterviewRecord> records = interviewRecordRepository
                .findAllByUser_IdOrderByInterview_IdAsc(user.getId()).stream()
                .filter(record -> !normalize(record.getAnswerText()).isEmpty())
                .toList();
        int from = Math.max(0, records.size() - properties.getMaxInterviews());
        List<InterviewAnswer> interviews = new ArrayList<>();
        for (InterviewRecord record : records.subList(from, records.size())) {
            String question = truncate(normalize(record.getInterview().getQuestion()),
                    properties.getMaxQuestionCharacters());
            String answer = truncate(normalize(record.getAnswerText()),
                    properties.getMaxAnswerCharacters());
            question = fit(question, remaining);
            remaining -= question.length();
            answer = fit(answer, remaining);
            remaining -= answer.length();
            if (!answer.isEmpty()) {
                interviews.add(new InterviewAnswer(record.getInterview().getId(), question, answer));
            }
            if (remaining == 0) break;
        }

        String balanceSummary = analysisJobRepository
                .findFirstByUserIdAndStatusOrderBySetNumberDesc(
                        user.getId(), ValueBalanceAnalysisJobStatus.COMPLETED)
                .map(job -> normalize(job.getPersonalitySummary()))
                .orElse("");
        balanceSummary = fit(balanceSummary, remaining);

        return new CloneProfileGenerationInput(
                selfIntroduction,
                mbti == null ? null : mbti.getMbti().name(),
                toAxisScores(mbti),
                List.copyOf(interviews),
                balanceSummary,
                promptVersion
        );
    }

    private MbtiAxisScores toAxisScores(MbtiProfile mbti) {
        if (mbti == null) return null;
        validateScore(mbti.getIeScore());
        validateScore(mbti.getNsScore());
        validateScore(mbti.getFtScore());
        validateScore(mbti.getPjScore());
        // Contract: a larger IE score means E; larger NS/FT/PJ scores mean S/T/J.
        return new MbtiAxisScores(
                mbti.getIeScore(),
                100 - mbti.getNsScore(),
                100 - mbti.getFtScore(),
                100 - mbti.getPjScore()
        );
    }

    private void validateScore(Integer score) {
        if (score == null || score < 0 || score > 100) {
            throw new CloneProfileSourceException("MBTI axis score must be between 0 and 100");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String fit(String value, int remaining) {
        if (remaining <= 0) return "";
        return truncate(value, remaining);
    }
}
