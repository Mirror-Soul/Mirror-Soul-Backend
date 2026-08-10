package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "sigungu",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sigungu_names",
                columnNames = {"sido_name", "sigungu_name"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sigungu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sido_name", nullable = false, length = 50)
    private String sidoName;

    @Column(name = "sigungu_name", nullable = false, length = 50)
    private String sigunguName;
}
