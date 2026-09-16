package com.mirrorsoul.mirrorsoul_api.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.Interview;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbeddingTextBuilderTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CloneRepository cloneRepository = mock(CloneRepository.class);
    private final InterviewRecordRepository interviewRecordRepository =
            mock(InterviewRecordRepository.class);
    private EmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new EmbeddingTextBuilder(
                userRepository,
                cloneRepository,
                interviewRecordRepository,
                new JobKoreanLabelMapper()
        );
    }

    @Test
    void buildsJobTextWithKoreanCategory() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getJob()).thenReturn(Job.IT_TECH);
        when(user.getJobDescription()).thenReturn("Spring Boot 백엔드 개발자");

        String text = builder.build(userUuid, EmbeddingType.JOB);

        assertEquals(
                "직업 분야: 기술 및 IT\n직무 설명: Spring Boot 백엔드 개발자",
                text
        );
    }

    @Test
    void buildsInterviewTextFromQuestionsAndAnswersInOrder() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        Interview firstInterview = mock(Interview.class);
        Interview secondInterview = mock(Interview.class);
        InterviewRecord firstRecord = mock(InterviewRecord.class);
        InterviewRecord secondRecord = mock(InterviewRecord.class);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(interviewRecordRepository.findAllByUser_IdOrderByInterview_IdAsc(1L))
                .thenReturn(List.of(firstRecord, secondRecord));
        when(firstRecord.getInterview()).thenReturn(firstInterview);
        when(firstInterview.getQuestion()).thenReturn("주말에는 주로 무엇을 하나요?");
        when(firstRecord.getAnswerText()).thenReturn("집에서 영화를 봅니다.");
        when(secondRecord.getInterview()).thenReturn(secondInterview);
        when(secondInterview.getQuestion()).thenReturn("가장 중요하게 생각하는 가치는?");
        when(secondRecord.getAnswerText()).thenReturn("신뢰입니다.");

        String text = builder.build(userUuid, EmbeddingType.INTERVIEW);

        assertEquals(
                """
                사용자 인터뷰 응답:

                질문: 주말에는 주로 무엇을 하나요?
                답변: 집에서 영화를 봅니다.

                질문: 가장 중요하게 생각하는 가치는?
                답변: 신뢰입니다.
                """.strip(),
                text
        );
    }
}
