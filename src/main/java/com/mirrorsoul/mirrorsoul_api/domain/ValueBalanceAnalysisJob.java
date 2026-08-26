package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAnalysisJobStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "value_balance_analysis_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_value_balance_analysis_jobs_user_set",
                columnNames = {"user_id", "set_number"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValueBalanceAnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_value_balance_analysis_jobs_user"))
    private User user;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValueBalanceAnalysisJobStatus status;

    @Column(name = "personality_summary", columnDefinition = "TEXT")
    private String personalitySummary;

    @Column(name = "sqs_message_id", length = 100)
    private String sqsMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ValueBalanceAnalysisJob(User user, int setNumber) {
        this.user = user;
        this.setNumber = setNumber;
        this.status = ValueBalanceAnalysisJobStatus.PENDING;
    }

    public static ValueBalanceAnalysisJob create(User user, int setNumber) {
        return new ValueBalanceAnalysisJob(user, setNumber);
    }

    public void markMessageSent(String messageId) {
        this.status = ValueBalanceAnalysisJobStatus.PROCESSING;
        this.sqsMessageId = messageId;
        this.errorMessage = null;
    }

    public void markDispatchFailed(String message) {
        this.status = ValueBalanceAnalysisJobStatus.FAILED;
        this.errorMessage = message;
    }

    public void complete(String summary) {
        this.status = ValueBalanceAnalysisJobStatus.COMPLETED;
        this.personalitySummary = summary;
        this.errorMessage = null;
        this.finishedAt = LocalDateTime.now();
    }
}
