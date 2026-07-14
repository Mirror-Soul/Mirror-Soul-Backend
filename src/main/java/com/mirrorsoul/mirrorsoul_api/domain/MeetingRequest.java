package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.MeetingRequestStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@Table(name = "meeting_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class MeetingRequest extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_user_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_call_id", nullable = false)
    private VideoCall videoCall;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingRequestStatus status;

    @Column(name = "active_pair_key", unique = true, length = 50)
    private String activePairKey;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public void accept() {
        status = MeetingRequestStatus.ACCEPTED;
        activePairKey = null;
        respondedAt = LocalDateTime.now();
    }

    public void reject() {
        status = MeetingRequestStatus.REJECTED;
        activePairKey = null;
        respondedAt = LocalDateTime.now();
    }
}
