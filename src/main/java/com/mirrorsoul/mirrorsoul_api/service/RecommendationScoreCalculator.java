package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.recommendation.VectorSimilarityScores;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScoreCalculator {

    private static final double JOB_WEIGHT = 0.15;
    private static final double AGE_WEIGHT = 0.10;
    private static final double REGION_WEIGHT = 0.10;
    private static final double PROFILE_WEIGHT = 0.20;
    private static final double CLONE_SUMMARY_WEIGHT = 0.10;
    private static final double INTERVIEW_WEIGHT = 0.20;

    // ConversationScore(0.15)는 대화 요약 파이프라인 개발 전까지 계산에서 제외한다.
    private static final double AGE_SIGMA_YEARS = 5.0;
    private static final double DISTANCE_LAMBDA_KM = 50.0;
    private static final double PREFERRED_REGION_SIGMA_KM = 50.0;
    private static final double REGION_DISTANCE_ALPHA = 0.5;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    public int calculate(
            LocalDate requesterBirthDate,
            LocalDate candidateBirthDate,
            Region requesterResidence,
            Region candidateResidence,
            List<Region> requesterPreferredRegions,
            VectorSimilarityScores vectorScores
    ) {
        WeightedScore score = new WeightedScore();

        if (vectorScores != null) {
            score.add(vectorScores.jobScore(), JOB_WEIGHT);
            score.add(vectorScores.profileScore(), PROFILE_WEIGHT);
            score.add(vectorScores.cloneSummaryScore(), CLONE_SUMMARY_WEIGHT);
            score.add(vectorScores.interviewScore(), INTERVIEW_WEIGHT);
        }

        score.add(ageScore(requesterBirthDate, candidateBirthDate), AGE_WEIGHT);
        score.add(regionScore(
                requesterResidence,
                candidateResidence,
                requesterPreferredRegions
        ), REGION_WEIGHT);

        return score.toPercentage();
    }

    Double ageScore(LocalDate requesterBirthDate, LocalDate candidateBirthDate) {
        if (requesterBirthDate == null || candidateBirthDate == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        int requesterAge = Period.between(requesterBirthDate, today).getYears();
        int candidateAge = Period.between(candidateBirthDate, today).getYears();
        double difference = requesterAge - candidateAge;
        return Math.exp(-(difference * difference)
                / (2.0 * AGE_SIGMA_YEARS * AGE_SIGMA_YEARS));
    }

    Double regionScore(
            Region requesterResidence,
            Region candidateResidence,
            List<Region> requesterPreferredRegions
    ) {
        if (!hasCoordinates(requesterResidence) || !hasCoordinates(candidateResidence)) {
            return null;
        }

        double residenceDistance = distanceKm(requesterResidence, candidateResidence);
        double distanceScore = Math.exp(-residenceDistance / DISTANCE_LAMBDA_KM);

        Double preferredScore = requesterPreferredRegions.stream()
                .filter(this::hasCoordinates)
                .mapToDouble(region -> distanceKm(region, candidateResidence))
                .min()
                .stream()
                .map(distance -> Math.exp(-(distance * distance)
                        / (2.0 * PREFERRED_REGION_SIGMA_KM * PREFERRED_REGION_SIGMA_KM)))
                .boxed()
                .findFirst()
                .orElse(null);

        if (preferredScore == null) {
            return distanceScore;
        }
        return REGION_DISTANCE_ALPHA * distanceScore
                + (1.0 - REGION_DISTANCE_ALPHA) * preferredScore;
    }

    private boolean hasCoordinates(Region region) {
        return region != null
                && region.getLatitude() != null
                && region.getLongitude() != null;
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
        return 2.0 * EARTH_RADIUS_KM
                * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
    }

    private static final class WeightedScore {
        private double weightedSum;
        private double availableWeight;

        void add(Double value, double weight) {
            if (value == null || !Double.isFinite(value)) {
                return;
            }
            weightedSum += Math.max(0.0, Math.min(1.0, value)) * weight;
            availableWeight += weight;
        }

        int toPercentage() {
            if (availableWeight == 0.0) {
                return 0;
            }
            return (int) Math.round(100.0 * weightedSum / availableWeight);
        }
    }
}
