package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(
        name = "interview_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_interview", columnNames = {"user_id", "interview_id"})
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_interview_record_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_interview_record_interview"))
    private Interview interview;

    @Column(name = "answer_audio_url", length = 500)
    private String answerAudioUrl;

    @Column(name = "answer_audio_object_key", length = 500)
    private String answerAudioObjectKey;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private InterviewRecord(
            User user,
            Interview interview,
            String answerAudioUrl,
            String answerAudioObjectKey,
            String answerText
    ) {
        this.user = user;
        this.interview = interview;
        this.answerAudioUrl = answerAudioUrl;
        this.answerAudioObjectKey = answerAudioObjectKey;
        this.answerText = answerText;
    }

    public static InterviewRecord create(
            User user,
            Interview interview,
            String answerAudioUrl,
            String answerAudioObjectKey,
            String answerText
    ) {
        return new InterviewRecord(user, interview, answerAudioUrl, answerAudioObjectKey, answerText);
    }

    public void updateAnswer(String answerAudioUrl, String answerAudioObjectKey, String answerText) {
        this.answerAudioUrl = answerAudioUrl;
        this.answerAudioObjectKey = answerAudioObjectKey;
        this.answerText = answerText;
    }
}
