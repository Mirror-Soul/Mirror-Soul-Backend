package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMatchAnalysisStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "call_match_analyses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class CallMatchAnalysis extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_call_id", nullable = false, unique = true)
    private VideoCall videoCall;

    @Column(name = "twin_similarity")
    private Integer twinSimilarity;

    @Column(name = "conversation_summary", columnDefinition = "TEXT")
    private String conversationSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_points", columnDefinition = "json")
    private List<String> summaryPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CallMatchAnalysisStatus status = CallMatchAnalysisStatus.PENDING;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
}
