package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.SwipeAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@Table(
        name = "swipe_histories",
        indexes = {
                @Index(
                        name = "idx_swipe_histories_swiper_target_created",
                        columnList = "swiper_user_id,target_user_id,created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class SwipeHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "swiper_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_swipe_histories_swiper")
    )
    private User swiper;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "target_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_swipe_histories_target")
    )
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SwipeAction action;
}
