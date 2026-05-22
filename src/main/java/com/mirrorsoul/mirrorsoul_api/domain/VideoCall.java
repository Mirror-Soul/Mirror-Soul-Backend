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

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VideoCallStatus status;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Builder
    private VideoCall(Clone clone, User user, String roomId, CallMediaType mediaType) {
        this.clone = clone;
        this.user = user;
        this.roomId = roomId;
        this.mediaType = mediaType;
        this.startedAt = LocalDateTime.now();
        this.status = VideoCallStatus.READY;
    }

    public void start() {
        this.status = VideoCallStatus.IN_PROGRESS;
    }

    public void complete() {
        LocalDateTime now = LocalDateTime.now();
        this.endedAt = now;
        this.durationSec = (int) Duration.between(this.startedAt, now).getSeconds();
        this.status = VideoCallStatus.COMPLETED;
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
}