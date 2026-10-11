package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "video_calls")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clone_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_video_calls_clone"))
    private Clone clone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_video_calls_user"))
    private User user;

    @Column(name = "room_id", nullable = false, unique = true, length = 100)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private CallMediaType mediaType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "connected_at") private LocalDateTime connectedAt;
    @Column(name = "billing_deadline_at") private LocalDateTime billingDeadlineAt;
    @Column(name = "billing_limit_seconds") private Integer billingLimitSeconds;
    @Column(name = "billing_end_reason", length = 30) private String billingEndReason;
    @Column(name = "billing_end_source", length = 10) private String billingEndSource;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VideoCallStatus status;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;
    @Column(name = "clone_version")
    private Long cloneVersion;
    @Column(name = "voice_training_job_id")
    private Long voiceTrainingJobId;
    @Column(name = "face_training_job_id")
    private Long faceTrainingJobId;
    @Column(name = "personality_revision")
    private Long personalityRevision;

    @Builder
    private VideoCall(Clone clone, User user, String roomId, CallMediaType mediaType) {
        this.clone = clone;
        this.correlationId = java.util.UUID.randomUUID().toString();
        this.cloneVersion = clone.getCloneVersion();
        this.voiceTrainingJobId = clone.getSimilarityVoiceJobId();
        this.faceTrainingJobId = clone.getSimilarityFaceJobId();
        this.personalityRevision = clone.getSimilarityProfileRevision();
        this.user = user;
        this.roomId = roomId;
        this.mediaType = mediaType;
        this.startedAt = LocalDateTime.now();
        this.status = VideoCallStatus.READY;
    }

    public void start() {
        start(LocalDateTime.now(), null);
    }

    public void start(LocalDateTime at, Integer limit) {
        if (this.status != VideoCallStatus.READY) return;
        at = at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        this.connectedAt = at;
        this.billingLimitSeconds = limit;
        this.billingDeadlineAt = limit == null ? null : at.plusSeconds(limit);
        this.status = VideoCallStatus.IN_PROGRESS;
    }

    public void complete() {
        finish(LocalDateTime.now(), "USER_HANGUP", "MEMBER");
    }

    public void finish(LocalDateTime at, String reason, String source) {
        at = at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (billingDeadlineAt != null && at.isAfter(billingDeadlineAt)) at = billingDeadlineAt;
        this.endedAt = at;
        this.durationSec = connectedAt == null ? 0 :
                Math.toIntExact(Math.max(0, Duration.between(connectedAt, at).getSeconds()));
        this.billingEndReason = reason;
        this.billingEndSource = source;
        this.status = connectedAt == null ? VideoCallStatus.CANCELLED :
                "NETWORK_ERROR".equals(reason) || "SERVER_TIMEOUT".equals(reason) ? VideoCallStatus.FAILED : VideoCallStatus.COMPLETED;
    }

    public void cancel() {
        LocalDateTime now = LocalDateTime.now();
        this.endedAt = now;
        this.durationSec = (int) Duration.between(this.startedAt, now).getSeconds();
        this.status = VideoCallStatus.CANCELLED;
    }

    public void fail() {
        LocalDateTime now = LocalDateTime.now();
        this.endedAt = now;
        this.durationSec = (int) Duration.between(this.startedAt, now).getSeconds();
        this.status = VideoCallStatus.FAILED;
    }

    public void updateRecordingUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String diagnosticCorrelationId() {
        return correlationId == null ? com.mirrorsoul.mirrorsoul_api.quality.QualitySupport.correlation("CALL-" + id).toString() : correlationId;
    }
}
