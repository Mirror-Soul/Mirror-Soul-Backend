package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_uuid", columnNames = "uuid")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private UUID uuid;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Setter
    @Column(length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Setter
    @Column(length = 30)
    private Job job;

    @Setter
    @Column(name = "job_description", length = 200)
    private String jobDescription;

    @Setter
    @Column(name = "job_certification_object_key", length = 500)
    private String jobCertificationObjectKey;

    @Setter
    @Column(name = "self_introduction", length = 500)
    private String selfIntroduction;

    @Builder.Default
    @Column(name = "remaining_talk_time", nullable = false)
    private Integer remainingTalkTime = 1800;

    @Builder.Default
    @Column(name = "opponent_voice_volume", nullable = false)
    private Integer opponentVoiceVolume = 50;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_speech_speed", nullable = false, length = 20)
    private SpeechSpeed opponentSpeechSpeed = SpeechSpeed.NORMAL;

    @Builder.Default
    @Column(name = "missed_call_notification_enabled", nullable = false)
    private Boolean missedCallNotificationEnabled = true;

    @Builder.Default
    @Column(name = "low_time_notification_enabled", nullable = false)
    private Boolean lowTimeNotificationEnabled = true;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Setter
    @Column(length = 100)
    private String region;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @Setter
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void clearRefreshToken() {
        this.refreshToken = null;
    }

    public void addTalkTime(int seconds) {
        this.remainingTalkTime = getSafeRemainingTalkTime() + seconds;
    }

    public void useTalkTime(int seconds) {
        this.remainingTalkTime = Math.max(0, getSafeRemainingTalkTime() - seconds);
    }

    public void updateAudioSettings(int opponentVoiceVolume, SpeechSpeed opponentSpeechSpeed) {
        this.opponentVoiceVolume = opponentVoiceVolume;
        this.opponentSpeechSpeed = opponentSpeechSpeed;
    }

    public void updateAlarmSettings(boolean missedCallNotificationEnabled, boolean lowTimeNotificationEnabled) {
        this.missedCallNotificationEnabled = missedCallNotificationEnabled;
        this.lowTimeNotificationEnabled = lowTimeNotificationEnabled;
    }

    public boolean hasTalkTime() {
        return getSafeRemainingTalkTime() > 0;
    }

    private int getSafeRemainingTalkTime() {
        return remainingTalkTime == null ? 0 : remainingTalkTime;
    }

    @PrePersist
    private void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
