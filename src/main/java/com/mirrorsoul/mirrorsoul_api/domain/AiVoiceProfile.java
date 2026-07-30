package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_voice_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiVoiceProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clone_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_voice_profiles_clone"))
    private Clone clone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_training_job_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_voice_profiles_voice_training_job"))
    private VoiceTrainingJob voiceTrainingJob;

    @Column(name = "elevenlabs_voice_id")
    private String elevenlabsVoiceId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "intro_audio_bucket", length = 100)
    private String introAudioBucket;

    @Column(name = "intro_audio_object_key", length = 500)
    private String introAudioObjectKey;

    @Column(name = "intro_audio_content_type", length = 100)
    private String introAudioContentType;

    @Column(name = "intro_audio_size_bytes")
    private Long introAudioSizeBytes;

    @Column(name = "intro_audio_duration_ms")
    private Integer introAudioDurationMs;

    private AiVoiceProfile(
            Clone clone,
            VoiceTrainingJob voiceTrainingJob,
            String elevenlabsVoiceId,
            String status,
            boolean active
    ) {
        this.clone = clone;
        this.voiceTrainingJob = voiceTrainingJob;
        this.elevenlabsVoiceId = elevenlabsVoiceId;
        this.status = status;
        this.active = active;
    }

    public static AiVoiceProfile create(
            Clone clone,
            VoiceTrainingJob voiceTrainingJob,
            String elevenlabsVoiceId,
            String status,
            boolean active
    ) {
        return new AiVoiceProfile(clone, voiceTrainingJob, elevenlabsVoiceId, status, active);
    }
}
