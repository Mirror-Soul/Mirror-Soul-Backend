package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@Table(
        name = "recommendation_exposures",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_recommendation_exposures_requester_target",
                        columnNames = {"requester_user_id", "target_user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_recommendation_exposures_requester_last_exposed",
                        columnList = "requester_user_id,last_exposed_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class RecommendationExposure extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "requester_user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_recommendation_exposures_requester")
    )
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "target_user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_recommendation_exposures_target")
    )
    private User target;

    @Column(name = "last_exposed_at", nullable = false)
    private LocalDateTime lastExposedAt;

    public void markExposedAt(LocalDateTime exposedAt) {
        this.lastExposedAt = exposedAt;
    }
}
