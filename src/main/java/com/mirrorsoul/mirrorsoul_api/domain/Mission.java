package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.MissionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "missions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_type", nullable = false, length = 30)
    private MissionType missionType;

    @Column(name = "reward_point", nullable = false)
    private Integer rewardPoint;
}
