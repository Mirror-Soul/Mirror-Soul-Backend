package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_face_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiFaceProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clone_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_face_profiles_clone"))
    private Clone clone;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "face_training_job_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_ai_face_profiles_face_training_job"))
    private FaceTrainingJob faceTrainingJob;

    @Column(name = "avatar_cache_object_key", length = 500)
    private String avatarCacheObjectKey;

    @Column(name = "preview_image_object_key", length = 500)
    private String previewImageObjectKey;

    @Column(name = "preview_video_object_key", length = 500)
    private String previewVideoObjectKey;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "face_similarity_score")
    private Double faceSimilarityScore;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
