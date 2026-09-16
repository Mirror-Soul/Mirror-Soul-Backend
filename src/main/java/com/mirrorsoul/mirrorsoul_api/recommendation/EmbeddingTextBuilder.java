package com.mirrorsoul.mirrorsoul_api.recommendation;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmbeddingTextBuilder {

    private final UserRepository userRepository;
    private final CloneRepository cloneRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final JobKoreanLabelMapper jobLabelMapper;

    public String build(UUID userUuid, EmbeddingType type) {
        return switch (type) {
            case JOB -> buildJobText(userUuid);
            case PROFILE -> buildProfileText(userUuid);
            case CLONE_SUMMARY -> buildCloneSummaryText(userUuid);
            case INTERVIEW -> buildInterviewText(userUuid);
            case CONVERSATION -> throw new IllegalArgumentException(
                    "Conversation embedding is not supported yet"
            );
        };
    }

    private String buildJobText(UUID userUuid) {
        User user = getUser(userUuid);
        return """
                직업 분야: %s
                직무 설명: %s
                """.formatted(
                jobLabelMapper.toKoreanLabel(user.getJob()),
                normalize(user.getJobDescription())
        ).strip();
    }

    private String buildProfileText(UUID userUuid) {
        User user = getUser(userUuid);
        return """
                사용자 자기소개:

                %s
                """.formatted(normalize(user.getSelfIntroduction())).strip();
    }

    private String buildCloneSummaryText(UUID userUuid) {
        Clone clone = cloneRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Clone not found for user: " + userUuid
                ));
        return """
                클론 요약:

                %s
                """.formatted(normalize(clone.getSummary())).strip();
    }

    private String buildInterviewText(UUID userUuid) {
        User user = getUser(userUuid);
        List<String> responses = interviewRecordRepository
                .findAllByUser_IdOrderByInterview_IdAsc(user.getId()).stream()
                .filter(record -> StringUtils.hasText(record.getAnswerText()))
                .map(this::toInterviewResponse)
                .toList();

        if (responses.isEmpty()) {
            return "";
        }
        return "사용자 인터뷰 응답:\n\n" + String.join("\n\n", responses);
    }

    private String toInterviewResponse(InterviewRecord record) {
        return "질문: %s\n답변: %s".formatted(
                normalize(record.getInterview().getQuestion()),
                normalize(record.getAnswerText())
        );
    }

    private User getUser(UUID userUuid) {
        return userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + userUuid
                ));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
