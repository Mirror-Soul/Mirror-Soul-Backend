package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(name = "talk_time_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TalkTimeTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_talk_time_transactions_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_call_id", unique = true,
            foreignKey = @ForeignKey(name = "fk_talk_time_transactions_call"))
    private VideoCall videoCall;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TalkTimeTransactionReason reason;

    @Column(name = "delta_seconds", nullable = false)
    private int deltaSeconds;

    @Column(name = "balance_after_seconds", nullable = false)
    private int balanceAfterSeconds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private TalkTimeTransaction(User user, VideoCall videoCall,
            TalkTimeTransactionReason reason, int deltaSeconds, int balanceAfterSeconds) {
        this.user = user;
        this.videoCall = videoCall;
        this.reason = reason;
        this.deltaSeconds = deltaSeconds;
        this.balanceAfterSeconds = balanceAfterSeconds;
    }

    public static TalkTimeTransaction record(User user, VideoCall videoCall,
            TalkTimeTransactionReason reason, int deltaSeconds) {
        return new TalkTimeTransaction(user, videoCall, reason,
                deltaSeconds, user.getRemainingTalkTime());
    }
}
