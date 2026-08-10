package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.Sigungu;
import com.mirrorsoul.mirrorsoul_api.recommendation.VectorSimilarityScores;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationScoreCalculatorTest {

    private final RecommendationScoreCalculator calculator =
            new RecommendationScoreCalculator();

    @Test
    void 동일한_나이와_지역이고_모든_벡터가_일치하면_100점이다() {
        LocalDate birthDate = LocalDate.now().minusYears(25);
        Region region = region(37.5665, 126.9780);
        when(region.getSidoName()).thenReturn("서울특별시");
        when(region.getSigunguName()).thenReturn("중구");
        Sigungu sigungu = sigungu("서울특별시", "중구");
        VectorSimilarityScores vectors = new VectorSimilarityScores(
                UUID.randomUUID(),
                1.0,
                1.0,
                1.0,
                1.0,
                1.0
        );

        int score = calculator.calculate(
                birthDate,
                birthDate,
                region,
                region,
                List.of(sigungu),
                vectors
        );

        assertThat(score).isEqualTo(100);
    }

    @Test
    void 대화점수는_현재_최종점수에_반영하지_않는다() {
        LocalDate birthDate = LocalDate.now().minusYears(25);
        VectorSimilarityScores vectors = new VectorSimilarityScores(
                UUID.randomUUID(),
                0.0,
                0.0,
                0.0,
                1.0,
                0.0
        );

        int score = calculator.calculate(
                birthDate,
                birthDate,
                null,
                null,
                List.of(),
                vectors
        );

        // 사용 가능한 가중치 0.75 중 AgeScore의 0.10만 획득한다.
        assertThat(score).isEqualTo(13);
    }

    @Test
    void 나이차가_커질수록_가우시안_점수가_감소한다() {
        LocalDate requester = LocalDate.now().minusYears(25);

        Double close = calculator.ageScore(
                requester,
                LocalDate.now().minusYears(27)
        );
        Double far = calculator.ageScore(
                requester,
                LocalDate.now().minusYears(40)
        );

        assertThat(close).isGreaterThan(far);
    }

    @Test
    void 후보자의_시군구가_선호지역과_일치하면_선호점수는_1이다() {
        Region requester = region(37.0, 127.0);
        Region candidate = region(37.1, 127.1);
        when(candidate.getSidoName()).thenReturn("서울특별시");
        when(candidate.getSigunguName()).thenReturn("강남구");

        Double score = calculator.regionScore(
                requester,
                candidate,
                List.of(sigungu("서울특별시", "강남구"))
        );

        double distanceOnly = Math.exp(-distanceKm(requester, candidate) / 50.0);
        assertThat(score).isEqualTo((distanceOnly + 1.0) / 2.0);
    }

    @Test
    void 후보자의_시군구가_선호지역과_다르면_선호점수는_0이다() {
        Region requester = region(37.0, 127.0);
        Region candidate = region(37.1, 127.1);
        when(candidate.getSidoName()).thenReturn("서울특별시");
        when(candidate.getSigunguName()).thenReturn("송파구");

        Double score = calculator.regionScore(
                requester,
                candidate,
                List.of(sigungu("서울특별시", "강남구"))
        );

        double distanceOnly = Math.exp(-distanceKm(requester, candidate) / 50.0);
        assertThat(score).isEqualTo(distanceOnly / 2.0);
    }

    private Region region(double latitude, double longitude) {
        Region region = mock(Region.class);
        when(region.getLatitude()).thenReturn(BigDecimal.valueOf(latitude));
        when(region.getLongitude()).thenReturn(BigDecimal.valueOf(longitude));
        return region;
    }

    private Sigungu sigungu(String sidoName, String sigunguName) {
        Sigungu sigungu = mock(Sigungu.class);
        when(sigungu.getSidoName()).thenReturn(sidoName);
        when(sigungu.getSigunguName()).thenReturn(sigunguName);
        return sigungu;
    }

    private double distanceKm(Region first, Region second) {
        double firstLatitude = Math.toRadians(first.getLatitude().doubleValue());
        double secondLatitude = Math.toRadians(second.getLatitude().doubleValue());
        double latitudeDifference = secondLatitude - firstLatitude;
        double longitudeDifference = Math.toRadians(
                second.getLongitude().doubleValue() - first.getLongitude().doubleValue()
        );
        double haversine = Math.pow(Math.sin(latitudeDifference / 2.0), 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.pow(Math.sin(longitudeDifference / 2.0), 2);
        return 2.0 * 6371.0088 * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
    }
}
