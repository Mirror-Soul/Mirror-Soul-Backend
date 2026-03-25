package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Speaker;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "talk_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TalkLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_call_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_talk_logs_video_call"))
    private VideoCall videoCall;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Speaker speaker;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
