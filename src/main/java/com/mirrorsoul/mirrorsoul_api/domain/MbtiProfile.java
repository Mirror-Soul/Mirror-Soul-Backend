package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    public static MbtiProfile create(
            User user,
            MbtiType mbti,
            Integer ieScore,
            Integer nsScore,
            Integer ftScore,
            Integer pjScore
    ) {
        return MbtiProfile.builder()
                .user(user)
                .mbti(mbti)
                .ieScore(ieScore)
                .nsScore(nsScore)
                .ftScore(ftScore)
                .pjScore(pjScore)
                .build();
    }

    public void update(
            MbtiType mbti,
            Integer ieScore,
            Integer nsScore,
            Integer ftScore,
            Integer pjScore
    ) {
        this.mbti = mbti;
        this.ieScore = ieScore;
        this.nsScore = nsScore;
        this.ftScore = ftScore;
        this.pjScore = pjScore;
    }
}
