package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "clones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Clone extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_clones_user")
    )
    private User user;

    @lombok.Builder.Default
    @Column(name = "sync_rate", nullable = false, precision = 4, scale = 1)
    private BigDecimal syncRate = new BigDecimal("0.0");

    @Column(name = "face_similarity_score", precision = 5, scale = 2)
    private BigDecimal faceSimilarityScore;

    @Column(name = "voice_similarity_score", precision = 5, scale = 2)
    private BigDecimal voiceSimilarityScore;

    @Column(name = "profile_similarity_score", precision = 5, scale = 2)
    private BigDecimal profileSimilarityScore;

    @Column(name = "data_reliability_score", precision = 5, scale = 2)
    private BigDecimal dataReliabilityScore;

    @lombok.Builder.Default
    @Column(name = "similarity_penalty", nullable = false, precision = 5, scale = 2)
    private BigDecimal similarityPenalty = BigDecimal.ZERO;

    @Column(name = "similarity_score_version", length = 50)
    private String similarityScoreVersion;

    @Column(name = "similarity_face_job_id")
    private Long similarityFaceJobId;

    @Column(name = "similarity_voice_job_id")
    private Long similarityVoiceJobId;

    @Column(name = "similarity_profile_revision")
    private Long similarityProfileRevision;

    @Column(name = "avatar_image_url", length = 500)
    private String avatarImageUrl;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "profile_source_hash", length = 64)
    private String profileSourceHash;

    @lombok.Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "personality_training_completed", nullable = false)
    private boolean personalityTrainingCompleted;

    public void updateProfile(String summary, String profileSourceHash) {
        this.summary = summary;
        this.profileSourceHash = profileSourceHash;
    }

    public void updatePersonalityTrainingCompleted(boolean completed) {
        personalityTrainingCompleted = completed;
    }

    public void refreshReadiness(boolean voiceActive, boolean faceReady) {
        status = voiceActive && faceReady && personalityTrainingCompleted ? "READY" : "PENDING";
    }

    public BigDecimal getVisibleSyncRate() {
        return "READY".equals(status) ? syncRate : null;
    }

    // All component changes require the clone row lock in the caller's transaction.
    public boolean updateFaceSimilarity(Long jobId, BigDecimal score) {
        requirePositive(jobId);
        if (similarityFaceJobId != null && jobId <= similarityFaceJobId) return false;
        CloneSimilarityCalculator.validateScore(score);
        similarityFaceJobId = jobId;
        faceSimilarityScore = score;
        recalculateSimilarity();
        return true;
    }

    public boolean updateVoiceSimilarity(Long jobId, BigDecimal score) {
        requirePositive(jobId);
        if (similarityVoiceJobId != null && jobId <= similarityVoiceJobId) return false;
        if (score == null) throw new IllegalArgumentException("Voice score is required");
        CloneSimilarityCalculator.validateScore(score);
        similarityVoiceJobId = jobId;
        voiceSimilarityScore = score;
        recalculateSimilarity();
        return true;
    }

    public boolean updateProfileSimilarity(Long revision, BigDecimal profile,
            BigDecimal reliability, BigDecimal penalty) {
        if (revision != null) requirePositive(revision);
        // Unversioned callbacks can initialize scores once, but cannot overwrite a newer result.
        long incoming = revision == null ? 0L : revision;
        if (similarityProfileRevision != null && incoming <= similarityProfileRevision) return false;
        if (profile == null || reliability == null || penalty == null) {
            throw new IllegalArgumentException("All profile scores are required");
        }
        CloneSimilarityCalculator.validateScore(profile);
        CloneSimilarityCalculator.validateScore(reliability);
        CloneSimilarityCalculator.validatePenalty(penalty);
        profileSimilarityScore = profile;
        dataReliabilityScore = reliability;
        similarityPenalty = penalty;
        similarityProfileRevision = incoming;
        recalculateSimilarity();
        return true;
    }

    private void recalculateSimilarity() {
        // Recalculate on every accepted result. Missing components contribute zero,
        // including for members whose previous total used the legacy calculation.
        syncRate = CloneSimilarityCalculator.calculate(faceSimilarityScore, voiceSimilarityScore,
                profileSimilarityScore, dataReliabilityScore, similarityPenalty);
        similarityScoreVersion = CloneSimilarityCalculator.VERSION;
    }

    private static void requirePositive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Source identifier must be positive");
    }
}
