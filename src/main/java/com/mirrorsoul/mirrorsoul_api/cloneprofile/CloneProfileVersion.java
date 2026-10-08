package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;
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
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "clone_profile_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CloneProfileVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clone_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_clone_profile_versions_clone"))
    private Clone clone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_job_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_clone_profile_versions_job"))
    private CloneProfileGenerationJob generationJob;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "personality_tags", nullable = false, columnDefinition = "json")
    private List<String> personalityTags;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private CloneProfileVersion(Clone clone, CloneProfileGenerationJob generationJob, int versionNumber,
            GeneratedCloneProfile generated) {
        this.clone = clone;
        this.generationJob = generationJob;
        this.versionNumber = versionNumber;
        this.sourceHash = generationJob.getSourceHash();
        this.promptVersion = generationJob.getPromptVersion();
        this.summary = generated.summary();
        this.personalityTags = List.copyOf(generated.personalityTags());
    }

    public static CloneProfileVersion record(Clone clone, CloneProfileGenerationJob generationJob,
            int versionNumber,
            GeneratedCloneProfile generated) {
        return new CloneProfileVersion(clone, generationJob, versionNumber, generated);
    }
}
