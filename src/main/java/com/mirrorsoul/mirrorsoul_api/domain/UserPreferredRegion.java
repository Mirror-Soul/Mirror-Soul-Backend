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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@Table(
        name = "user_preferred_region",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_preferred_region_user",
                columnNames = "user_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class UserPreferredRegion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_upr_user")
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "anchor_region_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_upr_anchor_region")
    )
    private Region anchorRegion;

    @Column(name = "nearby_count", nullable = false)
    private Integer nearbyCount;

    public void update(Region anchorRegion, int nearbyCount) {
        this.anchorRegion = anchorRegion;
        this.nearbyCount = nearbyCount;
    }
}
