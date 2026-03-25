package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "region",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_region_lawd_cd", columnNames = "lawd_cd")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lawd_cd", nullable = false, length = 10)
    private String lawdCd;

    @Column(name = "sido_name", nullable = false, length = 50)
    private String sidoName;

    @Column(name = "sigungu_name", nullable = false, length = 50)
    private String sigunguName;

    @Column(name = "eupmyeondong_name", nullable = false, length = 50)
    private String eupmyeondongName;
}