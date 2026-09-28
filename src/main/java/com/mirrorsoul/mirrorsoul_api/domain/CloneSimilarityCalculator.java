package com.mirrorsoul.mirrorsoul_api.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CloneSimilarityCalculator {
    public static final String VERSION = "clone-similarity-v1";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAXIMUM = new BigDecimal("95");

    private CloneSimilarityCalculator() {}

    public static BigDecimal calculate(BigDecimal face, BigDecimal voice, BigDecimal profile,
            BigDecimal reliability, BigDecimal penalty) {
        validateScore(face);
        validateScore(voice);
        validateScore(profile);
        validateScore(reliability);
        validatePenalty(penalty);
        BigDecimal raw = zero(face).add(zero(voice)).add(zero(profile)).multiply(new BigDecimal("0.30"))
                .add(zero(reliability).multiply(new BigDecimal("0.10"))).subtract(zero(penalty));
        return raw.multiply(new BigDecimal("0.95")).max(BigDecimal.ZERO).min(MAXIMUM)
                .setScale(1, RoundingMode.HALF_UP);
    }

    public static void validateScore(BigDecimal score) {
        validate(score, HUNDRED, "Component score");
    }

    public static void validatePenalty(BigDecimal penalty) {
        validate(penalty, new BigDecimal("999.99"), "Penalty");
    }

    private static void validate(BigDecimal value, BigDecimal maximum, String name) {
        if (value != null && (value.signum() < 0 || value.compareTo(maximum) > 0
                || value.stripTrailingZeros().scale() > 2)) {
            throw new IllegalArgumentException(name + " is out of range or has more than two decimal places");
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
