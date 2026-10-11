package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;

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

    @Builder.Default
    @Column(name = "matching_enabled", nullable = false)
    private Boolean matchingEnabled = true;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "residence_region_id",
            foreignKey = @ForeignKey(name = "fk_users_residence_region")
    )
    private Region residenceRegion;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "profile_image_object_key", length = 500)
    private String profileImageObjectKey;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Setter
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Builder.Default
    @Column(name="session_version",nullable=false)
    private Long sessionVersion = 0L;

    public void revokeAdministrativeSessions() { invalidateSessions(); }

    public void changeAdministrativeRole(UserRole next) { this.role = next; invalidateSessions(); }

    private void invalidateSessions() {
        sessionVersion++;
        clearRefreshToken();
    }

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_withdrawal", length = 20)
    private UserStatus statusBeforeWithdrawal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_suspension", length = 20)
    private UserStatus statusBeforeSuspension;

    @Column(name="safety_restricted_until")
    private LocalDateTime safetyRestrictedUntil;

    public boolean isSafetyRestricted(LocalDateTime now) {
        return safetyRestrictedUntil!=null && now.isBefore(safetyRestrictedUntil);
    }

    public boolean isAiWorkAllowed(LocalDateTime now) {
        return isAccessAllowed() && !isSafetyRestricted(now);
    }

    public void updateSafetyRestriction(LocalDateTime until) {
        safetyRestrictedUntil=until;
    }

    public boolean isAccessAllowed() {
        return status != UserStatus.INACTIVE && status != UserStatus.DELETED
                && status != UserStatus.SUSPENDED && status != UserStatus.WITHDRAWAL_CONFIRMED;
    }

    public UserStatus onboardingStatus() {
        UserStatus stage = status == UserStatus.INACTIVE || status == UserStatus.WITHDRAWAL_CONFIRMED || status == UserStatus.DELETED
                ? statusBeforeWithdrawal : status;
        return stage == UserStatus.SUSPENDED ? statusBeforeSuspension : stage;
    }

    public void suspend() {
        if (!isAccessAllowed()) throw new IllegalStateException("Account cannot be suspended.");
        statusBeforeSuspension = status;
        status = UserStatus.SUSPENDED;
        invalidateSessions();
    }

    public void unsuspend() {
        if (status != UserStatus.SUSPENDED) throw new IllegalStateException("Account is not suspended.");
        status = statusBeforeSuspension == null ? UserStatus.ACTIVE : statusBeforeSuspension;
        statusBeforeSuspension = null;
    }

    public void confirmWithdrawal() {
        if (status != UserStatus.INACTIVE) throw new IllegalStateException("Account is not withdrawn.");
        status = UserStatus.WITHDRAWAL_CONFIRMED;
        clearRefreshToken();
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void clearRefreshToken() {
        this.refreshToken = null;
    }

    public void deactivate(LocalDateTime withdrawnAt) {
        if (!isAccessAllowed() && status != UserStatus.SUSPENDED) {
            throw new IllegalStateException("Account cannot be withdrawn.");
        }
        this.statusBeforeWithdrawal = this.status;
        this.status = UserStatus.INACTIVE;
        this.withdrawnAt = withdrawnAt;
        invalidateSessions();
    }

    public boolean canRecover(LocalDateTime now) {
        return status == UserStatus.INACTIVE
                && withdrawnAt != null
                && now.isBefore(withdrawnAt.plusDays(30));
    }

    public void reactivate() {
        this.status = statusBeforeWithdrawal == null ? UserStatus.ACTIVE : statusBeforeWithdrawal;
        this.statusBeforeWithdrawal = null;
        this.withdrawnAt = null;
    }

    public void anonymize(LocalDateTime deletedAt) {
        this.email = "deleted-" + uuid + "@deleted.invalid";
        this.passwordHash = "DELETED:" + UUID.randomUUID();
        this.name = "탈퇴한 사용자";
        this.gender = null;
        this.job = null;
        this.jobDescription = null;
        this.jobCertificationObjectKey = null;
        this.selfIntroduction = null;
        this.birthDate = null;
        this.residenceRegion = null;
        this.profileImageUrl = null;
        this.profileImageObjectKey = null;
        this.lastActiveAt = null;
        this.refreshToken = null;
        this.matchingEnabled = false;
        this.status = UserStatus.DELETED;
        this.deletedAt = deletedAt;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        invalidateSessions();
    }

    public void addTalkTime(int seconds) {
        this.remainingTalkTime = Math.addExact(getSafeRemainingTalkTime(), seconds);
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

    public void updateMatchingEnabled(boolean matchingEnabled) {
        this.matchingEnabled = matchingEnabled;
    }

    public void updateResidenceRegion(Region residenceRegion) {
        this.residenceRegion = residenceRegion;
    }

    public void updateProfileImage(String profileImageUrl, String profileImageObjectKey) {
        this.profileImageUrl = profileImageUrl;
        this.profileImageObjectKey = profileImageObjectKey;
    }

    public void clearProfileImage() {
        this.profileImageUrl = null;
        this.profileImageObjectKey = null;
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
