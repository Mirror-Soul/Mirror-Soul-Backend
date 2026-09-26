package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobStatus;
import jakarta.persistence.*;
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
@Table(name = "face_training_jobs")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceTrainingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_face_training_jobs_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FaceTrainingJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FaceTrainingJobSource source;

    @Column(name = "sqs_message_id", length = 100)
    private String sqsMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_retryable")
    private Boolean errorRetryable;

    @OneToMany(mappedBy = "faceTrainingJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FaceTrainingJobFile> files = new ArrayList<>();

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

    private FaceTrainingJob(User user, FaceTrainingJobSource source) {
        this.user = user;
        this.source = source;
        this.status = FaceTrainingJobStatus.PENDING;
    }

    public static FaceTrainingJob create(User user, FaceTrainingJobSource source) {
        return new FaceTrainingJob(user, source);
    }

    public void addFile(FaceFile faceFile, String bucket, String objectKey) {
        files.add(FaceTrainingJobFile.create(this, faceFile, bucket, objectKey));
    }

    public void markMessageSent(String sqsMessageId) {
        this.sqsMessageId = sqsMessageId;
        this.errorMessage = null;
    }

    public boolean isTerminal() {
        return status == FaceTrainingJobStatus.COMPLETED || status == FaceTrainingJobStatus.FAILED;
    }

    public void markProcessing() {
        if (isTerminal()) return;
        status = FaceTrainingJobStatus.PROCESSING;
        if (startedAt == null) startedAt = LocalDateTime.now();
    }

    public void complete() {
        markProcessing();
        status = FaceTrainingJobStatus.COMPLETED;
        errorMessage = null;
        errorCode = null;
        errorRetryable = null;
        finishedAt = LocalDateTime.now();
    }

    public void recordFailure(String code, String message, boolean retryable) {
        if (isTerminal()) return;
        markProcessing();
        errorCode = code;
        errorMessage = message;
        errorRetryable = retryable;
        if (!retryable) {
            status = FaceTrainingJobStatus.FAILED;
            finishedAt = LocalDateTime.now();
        }
    }

    public void markDispatchFailed(String errorMessage) {
        this.status = FaceTrainingJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }
}
