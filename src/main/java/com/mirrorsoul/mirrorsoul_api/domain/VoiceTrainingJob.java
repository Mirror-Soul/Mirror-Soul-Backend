package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "voice_training_jobs")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceTrainingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_voice_training_jobs_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoiceTrainingJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VoiceTrainingJobSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_training_sentence_id",
            foreignKey = @ForeignKey(name = "fk_voice_training_jobs_sentence"))
    private VoiceTrainingSentence voiceTrainingSentence;

    @Column(name = "sqs_message_id", length = 100)
    private String sqsMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @OneToMany(mappedBy = "voiceTrainingJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VoiceTrainingJobFile> files = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private VoiceTrainingJob(User user, VoiceTrainingJobSource source) {
        this.user = user;
        this.source = source;
        this.status = VoiceTrainingJobStatus.PENDING;
    }

    public static VoiceTrainingJob create(User user) {
        return new VoiceTrainingJob(user, VoiceTrainingJobSource.ONBOARDING_INTERVIEW);
    }

    public static VoiceTrainingJob create(User user, VoiceTrainingJobSource source) {
        return new VoiceTrainingJob(user, source);
    }

    public static VoiceTrainingJob createVoiceUpdate(User user, VoiceTrainingSentence sentence) {
        VoiceTrainingJob job = new VoiceTrainingJob(user, VoiceTrainingJobSource.VOICE_UPDATE);
        job.voiceTrainingSentence = sentence;
        return job;
    }

    public void addFile(InterviewRecord interviewRecord, String bucket, String objectKey) {
        files.add(VoiceTrainingJobFile.create(this, interviewRecord, bucket, objectKey));
    }

    public void markMessageSent(String sqsMessageId) {
        this.sqsMessageId = sqsMessageId;
        this.errorMessage = null;
    }

    public void markDispatchFailed(String errorMessage) {
        this.status = VoiceTrainingJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }
}
