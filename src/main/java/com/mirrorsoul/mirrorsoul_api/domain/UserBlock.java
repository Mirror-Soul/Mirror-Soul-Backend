package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.Entity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_blocks_blocker_blocked",
                        columnNames = {"blocker_user_id", "blocked_user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_user_blocks_blocked_user", columnList = "blocked_user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class UserBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "blocker_user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_blocks_blocker")
    )
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "blocked_user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_blocks_blocked")
    )
    private User blocked;
}
