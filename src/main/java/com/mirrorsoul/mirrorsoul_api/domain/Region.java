package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "coordinate_source", length = 30)
    private String coordinateSource;

    @Column(name = "coordinate_updated_at")
    private LocalDateTime coordinateUpdatedAt;

    public void updateGeocodingData(
            String lawdCd,
            BigDecimal latitude,
            BigDecimal longitude,
            String coordinateSource
    ) {
        this.lawdCd = lawdCd;
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinateSource = coordinateSource;
        this.coordinateUpdatedAt = LocalDateTime.now();
    }
}
