package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.region.GeoDistanceUtils;
import com.mirrorsoul.mirrorsoul_api.recommendation.VectorSimilarityScores;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationScoreCalculatorTest {

    private final RecommendationScoreCalculator calculator =
            new RecommendationScoreCalculator();

    @Test
    void 동일한_나이와_지역이고_모든_벡터가_일치하면_100점이다() {
        LocalDate birthDate = LocalDate.now().minusYears(25);
        Region region = region(37.5665, 126.9780);
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
    void 거리가_가까울수록_지역점수가_높다() {
        Region requester = region(37.0, 127.0);
        Region closeCandidate = region(37.01, 127.01);
        Region farCandidate = region(38.0, 128.0);

        Double closeScore = calculator.regionScore(requester, closeCandidate);
        Double farScore = calculator.regionScore(requester, farCandidate);

        assertThat(closeScore).isGreaterThan(farScore);
    }

    @Test
    void 지역점수는_공용_거리계산식을_사용한다() {
        Region requester = region(37.0, 127.0);
        Region candidate = region(37.1, 127.1);

        Double score = calculator.regionScore(requester, candidate);

        double distance = GeoDistanceUtils.distanceKm(
                requester.getLatitude(), requester.getLongitude(),
                candidate.getLatitude(), candidate.getLongitude()
        );
        assertThat(score).isEqualTo(Math.exp(-distance / 50.0));
    }

    private Region region(double latitude, double longitude) {
        Region region = mock(Region.class);
        when(region.getLatitude()).thenReturn(BigDecimal.valueOf(latitude));
        when(region.getLongitude()).thenReturn(BigDecimal.valueOf(longitude));
        return region;
    }

}
