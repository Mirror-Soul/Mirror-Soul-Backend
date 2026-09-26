package com.mirrorsoul.mirrorsoul_api.recommendation;

import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EmbeddingTextBuilder {

    public Optional<String> buildJobText(User user) {
        if (user.getJob() == null) {
            return Optional.empty();
        }

        StringBuilder text = new StringBuilder()
                .append("직업 분야: ")
                .append(toKoreanJobName(user.getJob()));
        if (StringUtils.hasText(user.getJobDescription())) {
            text.append("\n직무 설명: ")
                    .append(normalize(user.getJobDescription()));
        }
        return Optional.of(text.toString());
    }

    public Optional<String> buildProfileText(User user) {
        if (!StringUtils.hasText(user.getSelfIntroduction())) {
            return Optional.empty();
        }
        return Optional.of("자기소개:\n" + normalize(user.getSelfIntroduction()));
    }

    public Optional<String> buildInterviewText(List<InterviewRecord> records) {
        if (records.isEmpty()
                || records.stream().anyMatch(record -> !StringUtils.hasText(record.getAnswerText()))) {
            return Optional.empty();
        }

        StringBuilder text = new StringBuilder("사용자 인터뷰 응답:\n");
        for (InterviewRecord record : records) {
            text.append("\n질문: ")
                    .append(normalize(record.getInterview().getQuestion()))
                    .append("\n답변: ")
                    .append(normalize(record.getAnswerText()))
                    .append('\n');
        }
        return Optional.of(text.toString().stripTrailing());
    }

    public Optional<String> buildCloneSummaryText(Clone clone) {
        if (!StringUtils.hasText(clone.getSummary())) {
            return Optional.empty();
        }
        return Optional.of("클론 요약:\n" + normalize(clone.getSummary()));
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private String toKoreanJobName(Job job) {
        return switch (job) {
            case IT_TECH -> "기술 및 IT";
            case DESIGN -> "디자인";
            case PLANNING_STRATEGY -> "기획 및 전략";
            case MARKETING_PR -> "마케팅 및 PR";
            case SALES_BUSINESS -> "영업 및 비즈니스";
            case HR_RECRUITING -> "인사 및 채용";
            case FINANCE_ACCOUNTING -> "재무 및 회계";
            case OPERATIONS_CS -> "운영 및 고객지원";
            case EDUCATION -> "교육";
            case MEDICAL_HEALTHCARE -> "의료 및 헬스케어";
            case MEDIA_CONTENT -> "미디어 및 콘텐츠";
            case LEGAL_PUBLIC -> "법률 및 공공";
            case MANUFACTURING_ENGINEERING -> "제조 및 엔지니어링";
            case STUDENT -> "학생";
            case FREELANCER -> "프리랜서";
            case ETC -> "기타";
        };
    }
}
