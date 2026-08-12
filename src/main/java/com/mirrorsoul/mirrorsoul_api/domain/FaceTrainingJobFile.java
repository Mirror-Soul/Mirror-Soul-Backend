package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "face_training_job_files")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaceTrainingJobFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "face_training_job_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_face_training_job_files_job"))
    private FaceTrainingJob faceTrainingJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "face_file_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_face_training_job_files_face_file"))
    private FaceFile faceFile;

    @Column(nullable = false, length = 100)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FaceTrainingJobFile(FaceTrainingJob job, FaceFile faceFile, String bucket, String objectKey) {
        this.faceTrainingJob = job;
        this.faceFile = faceFile;
        this.bucket = bucket;
        this.objectKey = objectKey;
    }

    public static FaceTrainingJobFile create(
            FaceTrainingJob job,
            FaceFile faceFile,
            String bucket,
            String objectKey
    ) {
        return new FaceTrainingJobFile(job, faceFile, bucket, objectKey);
    }
}
