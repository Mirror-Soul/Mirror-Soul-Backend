package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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

    @Column(name = "sync_rate", nullable = false)
    private Integer syncRate = 0;

    @Column(name = "avatar_image_url", length = 500)
    private String avatarImageUrl;

    @Column(columnDefinition = "TEXT")
    private String summary;
}
