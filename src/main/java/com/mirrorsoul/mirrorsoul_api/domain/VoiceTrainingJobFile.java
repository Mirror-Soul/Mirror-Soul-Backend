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
@Table(name = "voice_training_job_files")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceTrainingJobFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_training_job_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_voice_training_job_files_job"))
    private VoiceTrainingJob voiceTrainingJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_record_id",
            foreignKey = @ForeignKey(name = "fk_voice_training_job_files_interview_record"))
    private InterviewRecord interviewRecord;

    @Column(nullable = false, length = 100)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    private VoiceTrainingJobFile(
            VoiceTrainingJob voiceTrainingJob,
            InterviewRecord interviewRecord,
            String bucket,
            String objectKey
    ) {
        this.voiceTrainingJob = voiceTrainingJob;
        this.interviewRecord = interviewRecord;
        this.bucket = bucket;
        this.objectKey = objectKey;
    }

    public static VoiceTrainingJobFile create(
            VoiceTrainingJob voiceTrainingJob,
            InterviewRecord interviewRecord,
            String bucket,
            String objectKey
    ) {
        return new VoiceTrainingJobFile(voiceTrainingJob, interviewRecord, bucket, objectKey);
    }
}
