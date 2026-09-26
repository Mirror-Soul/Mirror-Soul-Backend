package com.mirrorsoul.mirrorsoul_api.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.Interview;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingTextBuilderTest {

    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();

    @Test
    void buildsKoreanJobText() {
        User user = mock(User.class);
        when(user.getJob()).thenReturn(Job.IT_TECH);
        when(user.getJobDescription()).thenReturn("  Spring Boot 백엔드 개발자  ");

        assertEquals(
                "직업 분야: 기술 및 IT\n직무 설명: Spring Boot 백엔드 개발자",
                builder.buildJobText(user).orElseThrow()
        );
    }

    @Test
    void buildsInterviewTextInProvidedOrder() {
        Interview firstInterview = mock(Interview.class);
        Interview secondInterview = mock(Interview.class);
        when(firstInterview.getQuestion()).thenReturn("주말에는 무엇을 하나요?");
        when(secondInterview.getQuestion()).thenReturn("어떤 대화를 좋아하나요?");

        InterviewRecord first = mock(InterviewRecord.class);
        InterviewRecord second = mock(InterviewRecord.class);
        when(first.getInterview()).thenReturn(firstInterview);
        when(first.getAnswerText()).thenReturn("영화를 봅니다.");
        when(second.getInterview()).thenReturn(secondInterview);
        when(second.getAnswerText()).thenReturn("솔직한 대화를 좋아합니다.");

        assertEquals(
                """
                사용자 인터뷰 응답:

                질문: 주말에는 무엇을 하나요?
                답변: 영화를 봅니다.

                질문: 어떤 대화를 좋아하나요?
                답변: 솔직한 대화를 좋아합니다.""",
                builder.buildInterviewText(List.of(first, second)).orElseThrow()
        );
    }

    @Test
    void skipsInterviewTextWhenAnyAnswerIsBlank() {
        InterviewRecord record = mock(InterviewRecord.class);
        when(record.getAnswerText()).thenReturn("  ");

        assertTrue(builder.buildInterviewText(List.of(record)).isEmpty());
    }

    @Test
    void buildsCloneSummaryWithoutPersonalityTags() {
        Clone clone = mock(Clone.class);
        when(clone.getSummary()).thenReturn("  새로운 경험을 즐기고 대화를 잘 들어줍니다.  ");

        assertEquals(
                "클론 요약:\n새로운 경험을 즐기고 대화를 잘 들어줍니다.",
                builder.buildCloneSummaryText(clone).orElseThrow()
        );
    }
}
