package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "mbti_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mbti_profile_user", columnNames = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MbtiProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_mbti_profile_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MbtiType mbti;

    @Column(name = "ie_score", nullable = false)
    private Integer ieScore;

    @Column(name = "ns_score", nullable = false)
    private Integer nsScore;

    @Column(name = "ft_score", nullable = false)
    private Integer ftScore;

    @Column(name = "pj_score", nullable = false)
    private Integer pjScore;
}
